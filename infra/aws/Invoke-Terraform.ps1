[CmdletBinding()]
param(
    [ValidateSet('fmt', 'init', 'validate', 'plan', 'show')]
    [string]$Action = 'plan',
    [string]$Profile = 'smarttraffic-project'
)
$ErrorActionPreference = 'Stop'
$image = 'hashicorp/terraform:1.13.5'
$mount = "${PSScriptRoot}:/work"
$argsForTerraform = switch ($Action) {
    'fmt' { @('fmt', '-recursive') }
    'init' { @('init', '-input=false') }
    'validate' { @('validate', '-no-color') }
    'plan' { @('plan', '-input=false', '-no-color', '-var=create_instances=true', '-out=lab.tfplan') }
    'show' { @('show', '-no-color', 'lab.tfplan') }
}
if ($Action -ne 'plan') {
    & docker run --rm -v $mount -w /work $image @argsForTerraform
    if ($LASTEXITCODE -ne 0) { throw "Terraform $Action failed" }
    return
}
# Export temporary login-session credentials only into this process environment.
# Values are never printed, placed in arguments, mounted from .aws, or saved to disk.
$names = @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $raw = & aws configure export-credentials --profile $Profile --format process
    if ($LASTEXITCODE -ne 0) { throw 'AWS session unavailable; authenticate the profile first.' }
    $credentials = $raw | ConvertFrom-Json
    $env:AWS_ACCESS_KEY_ID = $credentials.AccessKeyId
    $env:AWS_SECRET_ACCESS_KEY = $credentials.SecretAccessKey
    $env:AWS_SESSION_TOKEN = $credentials.SessionToken
    & docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN -v $mount -w /work $image @argsForTerraform
    if ($LASTEXITCODE -ne 0) { throw 'Terraform plan failed' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $credentials = $null
    $raw = $null
}
