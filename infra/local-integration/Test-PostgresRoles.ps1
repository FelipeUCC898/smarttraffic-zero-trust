#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fixtures = Join-Path $root '.tools/secure-e2e'
$envFile = Join-Path $fixtures 'runtime.env'
$envLines = Get-Content $envFile
foreach ($name in @('MIGRATOR_DB_PASSWORD', 'ARCHIVER_DB_PASSWORD')) {
    if (!($envLines | Where-Object { $_.StartsWith("$name=") })) {
        $value = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        Add-Content -LiteralPath $envFile -Value "$name=$value"
    }
}
$settings = @{}
foreach ($line in Get-Content $envFile) { $key, $value = $line -split '=',2; $settings[$key]=$value }
[IO.File]::WriteAllText((Join-Path $fixtures 'migrator-password'), $settings.MIGRATOR_DB_PASSWORD)
[IO.File]::WriteAllText((Join-Path $fixtures 'archiver-password'), $settings.ARCHIVER_DB_PASSWORD)
$compose = @('compose', '--env-file', $envFile, '-f', "$PSScriptRoot/compose.yml", '-f', "$root/infra/aws/zones/data-roles.yml", '-f', "$root/infra/aws/zones/core-roles.yml")
function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL role verification failed: $($args[0])" }
}
# Rebuild only the image changed by the new migration, using the local build cache.
Invoke-Docker @compose build archiver
Invoke-Docker @compose stop archiver
Invoke-Docker @compose up -d --no-deps --wait --wait-timeout 120 postgres
Invoke-Docker @compose exec -T postgres sh /run/config/configure-postgres-roles.sh
Invoke-Docker @compose up -d --no-deps --no-build archiver
Invoke-Docker run --rm --memory 1g --network smarttraffic-e2e_default --env-file $envFile -e SMARTTRAFFIC_POSTGRES_ROLES=true -e MAVEN_OPTS=-Xmx384m -v "${fixtures}:/fixtures:ro" -v "${root}:/workspace" -v "${root}/.tools/m2:/root/.m2" -w /workspace/infra/local-integration/probe maven:3.9-eclipse-temurin-17 mvn -B -ntp '-Dtest=PostgresRuntimeTest' '-DargLine=-Xmx256m' test
$started = [DateTime]::UtcNow.ToString('o')
$passed = $false
for ($i=0; $i -lt 45; $i++) {
    $rows = & docker @compose exec -T postgres psql -U smarttraffic -d smarttraffic -Atc "SELECT count(*) FROM traffic_observation WHERE sensor_id='secure-e2e-sensor' AND measured_at >= '$started';"
    if ($LASTEXITCODE -eq 0 -and [int]($rows | Select-Object -Last 1) -gt 0) { $passed=$true; break }
    Start-Sleep -Seconds 2
}
if (!$passed) { throw 'Restricted archiver did not persist fresh simulator events' }
Write-Output 'PASS: real archiver persisted fresh events using the restricted runtime role with separate Flyway credentials.'
