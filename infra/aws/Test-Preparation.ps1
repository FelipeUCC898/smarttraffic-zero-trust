#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
Get-ChildItem $PSScriptRoot -Filter *.ps1 | ForEach-Object {
    $parseErrors = $null
    [void][Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$null, [ref]$parseErrors)
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
}
$savedKey = $env:AWS_ACCESS_KEY_ID
$savedSecret = $env:AWS_SECRET_ACCESS_KEY
$savedToken = $env:AWS_SESSION_TOKEN
$savedRegion = $env:AWS_REGION
$savedDefaultRegion = $env:AWS_DEFAULT_REGION
try {
    $global:stPreparationaccount = '223455088753'
    $global:stPreparationarn = 'arn:aws:iam::223455088753:user/test-reviewer'
    $global:stPreparationcalls = @()
    $global:stPreparationterraformCalls = @()
    function aws {
        $global:stPreparationcalls += ,@($args)
        $global:LASTEXITCODE = 0
        if ($args[0] -eq 'sts') { return (@{Account=$global:stPreparationaccount;Arn=$global:stPreparationarn} | ConvertTo-Json) }
        if ($args[0] -eq 'configure') { return '{"AccessKeyId":"test-key","SecretAccessKey":"test-secret","SessionToken":"test-token"}' }
        if ($args[0] -in @('freetier','ec2','iam','service-quotas','budgets')) { return '{}' }
        throw 'Unexpected AWS API in preparation test'
    }
    function Invoke-FakeTerraform {
        $global:stPreparationterraformCalls += ,@($args)
        $global:LASTEXITCODE = 0
    }
    & "$PSScriptRoot/Invoke-Terraform.ps1" -Action plan -TerraformPath Invoke-FakeTerraform
    if ('-var=create_instances=false' -notin $global:stPreparationterraformCalls[0]) { throw 'Default plan enabled EC2' }
    & "$PSScriptRoot/Invoke-Terraform.ps1" -Action plan -TerraformPath Invoke-FakeTerraform -CreateInstances
    if ('-var=create_instances=true' -notin $global:stPreparationterraformCalls[1]) { throw 'Explicit review plan did not include EC2' }
    if ($env:AWS_ACCESS_KEY_ID -cne $savedKey -or $env:AWS_SECRET_ACCESS_KEY -cne $savedSecret -or
        $env:AWS_SESSION_TOKEN -cne $savedToken -or $env:AWS_REGION -cne $savedRegion -or
        $env:AWS_DEFAULT_REGION -cne $savedDefaultRegion) { throw 'Credential environment was not restored' }
    $global:stPreparationcalls = @()
    & "$PSScriptRoot/Get-Readiness.ps1" | Out-Null
    if ($global:stPreparationcalls.Count -ne 9 -or $global:stPreparationcalls[0][1] -ne 'get-caller-identity' -or
        $global:stPreparationcalls[1][1] -ne 'get-account-plan-state') { throw 'Inventory command arguments were lost' }
    foreach ($case in @('wrong-account', 'root')) {
        $global:stPreparationaccount = if ($case -eq 'wrong-account') { '000000000000' } else { '223455088753' }
        $global:stPreparationarn = if ($case -eq 'root') { 'arn:aws:iam::223455088753:root' } else { 'arn:aws:iam::000000000000:user/test' }
        $global:stPreparationcalls = @()
        $rejected = $false
        try { & "$PSScriptRoot/Invoke-Terraform.ps1" -Action plan -TerraformPath Invoke-FakeTerraform } catch { $rejected = $true }
        if (!$rejected -or $global:stPreparationcalls.Count -ne 1) { throw "Failed closed identity guard: $case" }
        $global:stPreparationcalls = @()
        $rejected = $false
        try { & "$PSScriptRoot/Get-Readiness.ps1" } catch { $rejected = $true }
        if (!$rejected -or $global:stPreparationcalls.Count -ne 1) { throw "Failed closed inventory guard: $case" }
    }
    Write-Output 'PASS: script syntax, default/explicit EC2 plans, credential restoration, wrong-account and root rejection (mocked, no AWS calls).'
    $global:stPreparationArch = 'amd64'
    function docker {
        $global:LASTEXITCODE = 0
        if ($args[1] -eq 'inspect') { return ('"sha256:' + ('a' * 64) + '" "linux" "' + $global:stPreparationArch + '"') }
        if ($args[1] -eq 'save') { [IO.File]::WriteAllText($args[3], 'synthetic-test-archive'); return }
        throw 'Unexpected Docker call in preparation test'
    }
    $scratch = Join-Path $root ('.tools/preparation-tests/' + [Guid]::NewGuid())
    & "$PSScriptRoot/Export-Images.ps1" -Zone app -OutputDirectory $scratch
    if (@(Get-Content "$scratch/app-imageids.txt").Count -ne 3) { throw 'Missing application image IDs' }
    foreach ($name in @('app-imageids.txt','app-SHA256SUMS')) {
        if ([byte]13 -in [IO.File]::ReadAllBytes((Join-Path $scratch $name))) { throw 'Image interchange manifests must use Linux LF line endings.' }
    }
    foreach ($line in Get-Content "$scratch/app-SHA256SUMS") {
        $hash, $file = $line -split '  ', 2
        if ((Get-FileHash (Join-Path $scratch $file)).Hash.ToLowerInvariant() -ne $hash) { throw 'Bundle checksum mismatch' }
    }
    $global:stPreparationArch = 'arm64'
    $rejected = $false
    try { & "$PSScriptRoot/Export-Images.ps1" -Zone auth -OutputDirectory "$scratch/rejected" } catch { $rejected = $true }
    if (!$rejected -or (Test-Path "$scratch/rejected")) { throw 'Foreign architecture was exported' }
    Write-Output 'PASS: per-zone image IDs, bundle checksums and wrong-architecture rejection (mocked Docker).'
} finally {
    $env:AWS_ACCESS_KEY_ID = $savedKey
    $env:AWS_SECRET_ACCESS_KEY = $savedSecret
    $env:AWS_SESSION_TOKEN = $savedToken
    $env:AWS_REGION = $savedRegion
    $env:AWS_DEFAULT_REGION = $savedDefaultRegion
    Remove-Variable -Scope Global -Name stPreparationaccount,stPreparationarn,stPreparationcalls,stPreparationterraformCalls,stPreparationArch -ErrorAction SilentlyContinue
}
