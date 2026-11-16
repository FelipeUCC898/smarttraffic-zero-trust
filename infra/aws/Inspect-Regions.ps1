#requires -Version 7.0
[CmdletBinding()]
param([string]$Profile = 'smarttraffic-project', [string]$OutputDirectory = "$PSScriptRoot/evidence/free-plan-review")
$ErrorActionPreference = 'Stop'
$callerRaw = & aws sts get-caller-identity --profile $Profile --region us-east-2 --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'STS unavailable' }
$caller = $callerRaw | ConvertFrom-Json
if ($caller.Account -ne '223455088753' -or $caller.Arn -match ':root$') { throw 'Unexpected account or root identity' }
$regionRaw = & aws ec2 describe-regions --profile $Profile --region us-east-2 --query 'Regions[].RegionName' --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'Region inventory unavailable' }
$regions = $regionRaw | ConvertFrom-Json
$results = $regions | ForEach-Object -ThrottleLimit 4 -Parallel {
    $region = $_
    $queries = [ordered]@{
        instances = @('ec2','describe-instances','--query','Reservations[].Instances[].{Id:InstanceId,State:State.Name,Type:InstanceType}')
        volumes = @('ec2','describe-volumes','--query','Volumes[].{Id:VolumeId,GiB:Size,State:State}')
        addresses = @('ec2','describe-addresses','--query','Addresses[].{Allocation:AllocationId,Instance:InstanceId}')
        snapshots = @('ec2','describe-snapshots','--owner-ids','self','--query','Snapshots[].{Id:SnapshotId,GiB:VolumeSize}')
    }
    $checks = [ordered]@{}
    foreach ($name in $queries.Keys) {
        $callArgs = $queries[$name]
        $raw = & aws @callArgs --profile $using:Profile --region $region --output json --no-cli-pager --cli-connect-timeout 10 --cli-read-timeout 20 2>&1
        if ($LASTEXITCODE -eq 0) {
            $checks[$name] = @{status='OK';value=(($raw -join "`n") | ConvertFrom-Json -NoEnumerate)}
        } else { $checks[$name] = @{status='UNVERIFIED';error=($raw -join "`n")} }
    }
    [pscustomobject]@{region=$region;checks=$checks}
}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
@{observedUtc=[DateTime]::UtcNow.ToString('o');regions=@($results | Sort-Object region)} |
    ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputDirectory 'regional-compute-storage.json') -Encoding utf8
Write-Output "Read-only EC2/EBS/address/snapshot scan completed in $($regions.Count) regions. Other services are outside this scan."
