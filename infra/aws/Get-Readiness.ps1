[CmdletBinding()]
param([string]$Profile = 'smarttraffic-project')
$ErrorActionPreference = 'Stop'
function Read-Aws([string[]]$Command) {
    $raw = & aws @Command --profile $Profile --region us-east-2 --output json --no-cli-pager --cli-connect-timeout 10 --cli-read-timeout 30
    if ($LASTEXITCODE -ne 0) { throw "AWS read failed: $($Command[0]) $($Command[1]). No deployment authorized." }
    return $raw | ConvertFrom-Json
}
$identity = Read-Aws -Command @('sts', 'get-caller-identity')
if ($identity.Account -ne '223455088753' -or $identity.Arn -match ':root$') {
    throw 'Expected account 223455088753 with a non-root identity; refusing further AWS calls.'
}
Write-Output "Verified account $($identity.Account), non-root identity."
# Read-only APIs. No Cost Explorer, resource creation or credit consumption test.
Read-Aws -Command @('freetier', 'get-account-plan-state')
Read-Aws -Command @('freetier', 'get-free-tier-usage')
Read-Aws -Command @('ec2', 'describe-instances', '--query', 'Reservations[].Instances[].{Id:InstanceId,State:State.Name,Type:InstanceType,Project:Tags[?Key==`Project`].Value|[0]}')
Read-Aws -Command @('ec2', 'describe-volumes', '--query', 'Volumes[].{Id:VolumeId,GiB:Size,State:State,Encrypted:Encrypted}')
Read-Aws -Command @('ec2', 'describe-addresses', '--query', 'Addresses[].{Allocation:AllocationId,Instance:InstanceId}')
Read-Aws -Command @('iam', 'list-users', '--query', 'Users[].{Name:UserName,Created:CreateDate}')
Read-Aws -Command @('service-quotas', 'get-service-quota', '--service-code', 'ec2', '--quota-code', 'L-1216C47A')
Read-Aws -Command @('budgets', 'describe-budgets', '--account-id', '223455088753')
Write-Output 'Read-only inventory complete. Credit expiry/eligibility, current prices and explicit approval are still required before deployment.'
