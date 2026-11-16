#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Profile = 'smarttraffic-project',
    [string]$OutputDirectory = "$PSScriptRoot/evidence/free-plan-review"
)
$ErrorActionPreference = 'Stop'
# Explicit read-only allowlist; never export credentials or invoke Cost Explorer.
$checks = [ordered]@{
    plan = @('freetier', 'get-account-plan-state')
    freeUsage = @('freetier', 'get-free-tier-usage')
    organization = @('organizations', 'describe-organization', '--query', 'Organization.{Id:Id,FeatureSet:FeatureSet,ManagementAccountId:MasterAccountId}')
    instances = @('ec2', 'describe-instances', '--query', 'Reservations[].Instances[].{Id:InstanceId,State:State.Name,Type:InstanceType,AZ:Placement.AvailabilityZone,Project:Tags[?Key==`Project`].Value|[0]}')
    volumes = @('ec2', 'describe-volumes', '--query', 'Volumes[].{Id:VolumeId,GiB:Size,Type:VolumeType,State:State,Encrypted:Encrypted}')
    addresses = @('ec2', 'describe-addresses', '--query', 'Addresses[].{Allocation:AllocationId,Instance:InstanceId}')
    snapshots = @('ec2', 'describe-snapshots', '--owner-ids', 'self', '--query', 'Snapshots[].{Id:SnapshotId,GiB:VolumeSize,State:State}')
    vpcs = @('ec2', 'describe-vpcs', '--query', 'Vpcs[].{Id:VpcId,Default:IsDefault,Cidr:CidrBlock}')
    natGateways = @('ec2', 'describe-nat-gateways', '--query', 'NatGateways[].{Id:NatGatewayId,State:State}')
    endpoints = @('ec2', 'describe-vpc-endpoints', '--query', 'VpcEndpoints[].{Id:VpcEndpointId,Type:VpcEndpointType,Service:ServiceName,State:State}')
    eligibleTypes = @('ec2', 'describe-instance-types', '--filters', 'Name=free-tier-eligible,Values=true', '--query', 'InstanceTypes[].{Type:InstanceType,vCPU:VCpuInfo.DefaultVCpus,MiB:MemoryInfo.SizeInMiB}')
    offerings = @('ec2', 'describe-instance-type-offerings', '--location-type', 'availability-zone', '--filters', 'Name=location,Values=us-east-2a', 'Name=instance-type,Values=t3.small,m7i-flex.large,c7i-flex.large')
    standardQuota = @('service-quotas', 'get-service-quota', '--service-code', 'ec2', '--quota-code', 'L-1216C47A')
    budgets = @('budgets', 'describe-budgets', '--account-id', '223455088753')
    amiParameter = @('ssm', 'get-parameter', '--name', '/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64', '--query', 'Parameter.Value')
    databases = @('rds', 'describe-db-instances', '--query', 'DBInstances[].{Id:DBInstanceIdentifier,Class:DBInstanceClass,State:DBInstanceStatus}')
    loadBalancers = @('elbv2', 'describe-load-balancers', '--query', 'LoadBalancers[].{Name:LoadBalancerName,Type:Type,State:State.Code}')
    mq = @('mq', 'list-brokers')
    kafka = @('kafka', 'list-clusters-v2', '--query', 'ClusterInfoList[].{Name:ClusterName,State:State,Type:ClusterType}')
}
function Invoke-Read([string[]]$Arguments, [string]$Region = 'us-east-2') {
    $raw = & aws @Arguments --profile $Profile --region $Region --output json --no-cli-pager --cli-connect-timeout 10 --cli-read-timeout 30 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($raw -join "`n") }
    return ($raw -join "`n") | ConvertFrom-Json -NoEnumerate
}
$identity = Invoke-Read @('sts', 'get-caller-identity')
if ($identity.Account -ne '223455088753' -or $identity.Arn -match ':root$') {
    throw 'Expected project account and non-root identity; no further calls made.'
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$results = [ordered]@{ observedUtc = [DateTime]::UtcNow.ToString('o'); region = 'us-east-2'; identity = $identity; checks = [ordered]@{} }
foreach ($name in $checks.Keys) {
    try {
        $value = Invoke-Read $checks[$name]
        $results.checks[$name] = @{ status = 'OK'; value = $value }
        Write-Output "OK: $name"
    } catch {
        $results.checks[$name] = @{ status = 'UNVERIFIED'; error = $_.Exception.Message }
        Write-Output "UNVERIFIED: $name - $($_.Exception.Message)"
    }
}
if ($results.checks.amiParameter.status -eq 'OK') {
    try {
        $results.checks.ami = @{ status = 'OK'; value = (Invoke-Read @('ec2', 'describe-images', '--image-ids', $results.checks.amiParameter.value, '--query', 'Images[].{Id:ImageId,Name:Name,Architecture:Architecture,Owner:OwnerId,FreeTierEligible:FreeTierEligible}')) }
    } catch { $results.checks.ami = @{ status = 'UNVERIFIED'; error = $_.Exception.Message } }
}
foreach ($instanceType in @('t3.small', 'm7i-flex.large', 'c7i-flex.large')) {
    try {
        $products = Invoke-Read -Region 'us-east-1' -Arguments @('pricing', 'get-products', '--service-code', 'AmazonEC2', '--filters',
            'Type=TERM_MATCH,Field=regionCode,Value=us-east-2', "Type=TERM_MATCH,Field=instanceType,Value=$instanceType",
            'Type=TERM_MATCH,Field=operatingSystem,Value=Linux', 'Type=TERM_MATCH,Field=tenancy,Value=Shared',
            'Type=TERM_MATCH,Field=preInstalledSw,Value=NA', 'Type=TERM_MATCH,Field=capacitystatus,Value=Used')
        $rates = @(foreach ($item in $products.PriceList) {
            $product = $item | ConvertFrom-Json
            foreach ($term in $product.terms.OnDemand.PSObject.Properties.Value) {
                foreach ($dimension in $term.priceDimensions.PSObject.Properties.Value) {
                    [pscustomobject]@{ sku = $product.product.sku; type = $instanceType; unit = $dimension.unit; usd = $dimension.pricePerUnit.USD; description = $dimension.description }
                }
            }
        })
        $results.checks["price-$instanceType"] = @{ status = 'OK'; value = $rates }
        Write-Output "OK: price-$instanceType"
    } catch {
        $results.checks["price-$instanceType"] = @{ status = 'UNVERIFIED'; error = $_.Exception.Message }
        Write-Output "UNVERIFIED: price-$instanceType - $($_.Exception.Message)"
    }
}
$destination = Join-Path $OutputDirectory 'aws-read-only.json'
$results | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $destination -Encoding utf8
Write-Output "Read-only evidence saved to $destination. No deployment or credit use authorized."
