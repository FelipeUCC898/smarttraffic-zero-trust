mock_provider "aws" {}

override_data {
  target = data.aws_ssm_parameter.ami
  values = { value = "ami-0123456789abcdef0" }
}

run "no_instances_by_default" {
  command = plan
  assert {
    condition     = length(aws_instance.zone) == 0
    error_message = "The default plan must never enable EC2 instances."
  }
}

run "five_zone_security_and_stop_guard" {
  command = plan
  variables { create_instances = true }
  assert {
    condition     = length(aws_instance.zone) == 5
    error_message = "Preserve the approved five-zone architecture."
  }
  assert {
    condition = alltrue([for vm in aws_instance.zone :
      vm.metadata_options[0].http_tokens == "required" &&
      vm.root_block_device[0].encrypted &&
      !vm.root_block_device[0].delete_on_termination &&
      vm.instance_initiated_shutdown_behavior == "stop"
    ])
    error_message = "Every VM needs IMDSv2, encrypted retained disks and stop-on-poweroff."
  }
  assert {
    condition = alltrue([for vm in aws_instance.zone :
      strcontains(vm.user_data, "OnBootSec=60min") &&
      strcontains(vm.user_data, "OnCalendar=2026-11-23 05:00:00 UTC") &&
      strcontains(vm.user_data, "Persistent=true") &&
      strcontains(vm.user_data, "Requires=smarttraffic-firewall.service") &&
      strcontains(vm.user_data, "sha256sum --check --status")
    ])
    error_message = "Rendered bootstrap must include stop, firewall and Compose verification."
  }
  assert {
    condition = alltrue([for rule in aws_vpc_security_group_ingress_rule.service :
      contains([8443, 9093, 5671, 5432], rule.from_port) && rule.cidr_ipv4 == null
    ])
    error_message = "Ingress must use service-group references and approved TLS ports."
  }
  assert {
    condition = alltrue([for name, vm in aws_instance.zone :
      !startswith(vm.instance_type, "t3.") || vm.credit_specification[0].cpu_credits == "standard"
    ])
    error_message = "T3 must not incur Unlimited CPU-credit surcharges."
  }
}

run "reject_expired_cutoff" {
  command = plan
  variables {
    create_instances = true
    lab_deadline_utc = "2020-01-01T00:00:00Z"
  }
  expect_failures = [aws_instance.zone]
}

run "reject_unbounded_window" {
  command = plan
  variables { lab_duration_minutes = 481 }
  expect_failures = [var.lab_duration_minutes]
}

run "custom_window" {
  command = plan
  variables {
    create_instances     = true
    lab_duration_minutes = 30
  }
  assert {
    condition     = strcontains(aws_instance.zone["auth"].user_data, "OnBootSec=30min")
    error_message = "The reviewed lab duration must reach the rendered timer."
  }
}
