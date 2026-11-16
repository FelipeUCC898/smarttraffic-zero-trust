terraform {
  required_version = ">= 1.10, < 2.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region              = "us-east-2"
  allowed_account_ids = ["223455088753"]
  default_tags {
    tags = { Project = "smarttraffic", Environment = "lab", ManagedBy = "terraform" }
  }
}

variable "create_instances" {
  description = "Enable only after reviewing costs and the application deployment prerequisites."
  type        = bool
  default     = false
}

variable "lab_duration_minutes" {
  description = "Power off each lab VM after this many minutes from every boot; EBS remains billable."
  type        = number
  default     = 60
  validation {
    condition     = var.lab_duration_minutes >= 1 && var.lab_duration_minutes <= 480 && floor(var.lab_duration_minutes) == var.lab_duration_minutes
    error_message = "The lab window must be a whole number of minutes between 1 and 480."
  }
}

variable "lab_deadline_utc" {
  description = "Absolute lab cutoff: end of Sunday 2026-11-22 in America/Bogota. Prepared only; no automation is active until an approved apply."
  type        = string
  default     = "2026-11-23T05:00:00Z"
  validation {
    condition     = can(formatdate("YYYY-MM-DD hh:mm:ss", var.lab_deadline_utc))
    error_message = "Supply the absolute cutoff as an RFC3339 timestamp."
  }
}

locals {
  zones = {
    auth   = { ip = "10.42.10.10", type = "t3.small", disk = 16 }
    data   = { ip = "10.42.10.11", type = "m7i-flex.large", disk = 40 }
    ingest = { ip = "10.42.10.12", type = "t3.small", disk = 16 }
    core   = { ip = "10.42.10.13", type = "c7i-flex.large", disk = 24 }
    app    = { ip = "10.42.10.14", type = "t3.small", disk = 24 }
  }
  flows = {
    ingest_auth  = { from = "ingest", to = "auth", port = 8443 }
    core_auth    = { from = "core", to = "auth", port = 8443 }
    app_auth     = { from = "app", to = "auth", port = 8443 }
    data_jwks    = { from = "data", to = "auth", port = 8443 }
    ingest_kafka = { from = "ingest", to = "data", port = 9093 }
    core_kafka   = { from = "core", to = "data", port = 9093 }
    core_amqp    = { from = "core", to = "data", port = 5671 }
    app_amqp     = { from = "app", to = "data", port = 5671 }
    core_pg      = { from = "core", to = "data", port = 5432 }
  }
}

resource "aws_vpc" "lab" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "smarttraffic-lab" }
}

resource "aws_subnet" "lab" {
  vpc_id                  = aws_vpc.lab.id
  cidr_block              = "10.42.10.0/24"
  availability_zone       = "us-east-2a"
  map_public_ip_on_launch = false
  tags                    = { Name = "smarttraffic-lab" }
}

resource "aws_internet_gateway" "lab" {
  vpc_id = aws_vpc.lab.id
}

resource "aws_route_table" "lab" {
  vpc_id = aws_vpc.lab.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.lab.id
  }
}

resource "aws_route_table_association" "lab" {
  subnet_id      = aws_subnet.lab.id
  route_table_id = aws_route_table.lab.id
}

resource "aws_security_group" "zone" {
  for_each    = local.zones
  name_prefix = "smarttraffic-${each.key}-"
  description = "Dedicated security zone ${each.key}; no public inbound access"
  vpc_id      = aws_vpc.lab.id
  tags        = { Name = "vm-${each.key}" }
}

resource "aws_vpc_security_group_ingress_rule" "service" {
  for_each                     = local.flows
  security_group_id            = aws_security_group.zone[each.value.to].id
  referenced_security_group_id = aws_security_group.zone[each.value.from].id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  description                  = each.key
}

resource "aws_vpc_security_group_egress_rule" "service" {
  for_each                     = local.flows
  security_group_id            = aws_security_group.zone[each.value.from].id
  referenced_security_group_id = aws_security_group.zone[each.value.to].id
  ip_protocol                  = "tcp"
  from_port                    = each.value.port
  to_port                      = each.value.port
  description                  = each.key
}

# Lab maintenance exception: SSM and package/image downloads over HTTPS.
# No NAT gateway or paid interface endpoints; no inbound public rules.
resource "aws_vpc_security_group_egress_rule" "maintenance" {
  for_each          = local.zones
  security_group_id = aws_security_group.zone[each.key].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  description       = "SSM and HTTPS maintenance; documented lab exception"
}

resource "aws_iam_role" "zone" {
  for_each = local.zones
  name     = "smarttraffic-lab-${each.key}"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  for_each   = local.zones
  role       = aws_iam_role.zone[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "zone" {
  for_each = local.zones
  name     = "smarttraffic-lab-${each.key}"
  role     = aws_iam_role.zone[each.key].name
}

data "aws_ssm_parameter" "ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "zone" {
  for_each                    = var.create_instances ? local.zones : {}
  ami                         = data.aws_ssm_parameter.ami.value
  instance_type               = each.value.type
  subnet_id                   = aws_subnet.lab.id
  private_ip                  = each.value.ip
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.zone[each.key].id]
  iam_instance_profile        = aws_iam_instance_profile.zone[each.key].name
  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/bootstrap.sh.tftpl", {
    zone                 = each.key
    lab_duration_minutes = var.lab_duration_minutes
    deadline_calendar    = "${formatdate("YYYY-MM-DD hh:mm:ss", var.lab_deadline_utc)} UTC"
    deadline_epoch       = formatdate("YYYY-MM-DD'T'hh:mm:ssZ", var.lab_deadline_utc)
    incoming             = [for f in values(local.flows) : "ip saddr ${local.zones[f.from].ip} tcp dport ${f.port} accept" if f.to == each.key]
    outgoing             = [for f in values(local.flows) : "ip daddr ${local.zones[f.to].ip} tcp dport ${f.port} accept" if f.from == each.key]
  })
  instance_initiated_shutdown_behavior = "stop"
  lifecycle {
    precondition {
      condition     = timecmp(var.lab_deadline_utc, plantimestamp()) > 0
      error_message = "The lab cutoff has passed. Review the window and costs before planning new instances."
    }
  }
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }
  dynamic "credit_specification" {
    for_each = startswith(each.value.type, "t3.") ? [1] : []
    content { cpu_credits = "standard" }
  }
  root_block_device {
    encrypted             = true
    volume_type           = "gp3"
    volume_size           = each.value.disk
    delete_on_termination = false
  }
  tags        = { Name = "vm-${each.key}", Zone = each.key }
  volume_tags = { Project = "smarttraffic", Environment = "lab", Zone = each.key }
  depends_on  = [aws_route_table_association.lab, aws_iam_role_policy_attachment.ssm, aws_vpc_security_group_egress_rule.maintenance]
}

output "zones" {
  value = { for name, vm in aws_instance.zone : name => { id = vm.id, private_ip = vm.private_ip } }
}

output "vpc_id" { value = aws_vpc.lab.id }
