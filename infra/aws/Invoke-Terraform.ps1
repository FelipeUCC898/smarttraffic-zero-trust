[CmdletBinding()]
param(
    [ValidateSet('fmt', 'init', 'validate', 'plan', 'show')]
    [string]$Action = 'plan',
    [string]$Profile = 'smarttraffic-project',
    [ValidateSet('Native', 'Docker')]
    [string]$Runtime = 'Native',
    [string]$TerraformPath = 'terraform',
    [switch]$CreateInstances
)
$ErrorActionPreference = 'Stop'
$image = 'hashicorp/terraform:1.13.5'
$mount = "${PSScriptRoot}:/work"
$argsForTerraform = switch ($Action) {
    'fmt' { @('fmt', '-recursive') }
    'init' { @('init', '-input=false') }
    'validate' { @('validate', '-no-color') }
    'plan' { @('plan', '-input=false', '-no-color', "-var=create_instances=$($CreateInstances.IsPresent.ToString().ToLowerInvariant())", '-out=lab.tfplan') }
    'show' { @('show', '-no-color', 'lab.tfplan') }
}
function Invoke-TerraformCommand {
    if ($Runtime -eq 'Native') {
        & $TerraformPath "-chdir=$PSScriptRoot" @argsForTerraform
    } else {
        $credentialArgs = @()
        if ($Action -eq 'plan') { $credentialArgs = @('-e', 'AWS_ACCESS_KEY_ID', '-e', 'AWS_SECRET_ACCESS_KEY', '-e', 'AWS_SESSION_TOKEN', '-e', 'AWS_REGION', '-e', 'AWS_DEFAULT_REGION') }
        & docker run --rm @credentialArgs -v $mount -w /work $image @argsForTerraform
    }
    if ($LASTEXITCODE -ne 0) { throw "Terraform $Action failed" }
}
if ($Action -ne 'plan') {
    Invoke-TerraformCommand
    return
}
# Export temporary login-session credentials only into this process environment.
# Values are never printed, placed in arguments, mounted from .aws, or saved to disk.
$names = @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN', 'AWS_REGION', 'AWS_DEFAULT_REGION')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $identity = & aws sts get-caller-identity --profile $Profile --region us-east-2 --output json --no-cli-pager
    if ($LASTEXITCODE -ne 0) { throw 'AWS session unavailable; authenticate the profile first.' }
    $caller = $identity | ConvertFrom-Json
    if ($caller.Account -ne '223455088753' -or $caller.Arn -match ':root$') { throw 'Expected the project account with a non-root identity; refusing to plan.' }
    $raw = & aws configure export-credentials --profile $Profile --format process
    if ($LASTEXITCODE -ne 0) { throw 'AWS session unavailable; authenticate the profile first.' }
    $credentials = $raw | ConvertFrom-Json
    $env:AWS_ACCESS_KEY_ID = $credentials.AccessKeyId
    $env:AWS_SECRET_ACCESS_KEY = $credentials.SecretAccessKey
    $env:AWS_SESSION_TOKEN = $credentials.SessionToken
    $env:AWS_REGION = 'us-east-2'
    $env:AWS_DEFAULT_REGION = 'us-east-2'
    Invoke-TerraformCommand
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $credentials = $null
    $raw = $null
}
