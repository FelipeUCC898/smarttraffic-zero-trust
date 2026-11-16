#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fixtures = Join-Path $root '.tools/secure-e2e'
$envFile = Join-Path $fixtures 'runtime.env'
$compose = @('compose', '--env-file', $envFile, '-f', "$PSScriptRoot/compose.yml")
function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Local flow failed: $($args[0])" }
}
# Pause only this harness's simulator while publishing controlled state transitions.
Invoke-Docker @compose stop simulator
try {
    Invoke-Docker run --rm --memory 1g --network smarttraffic-e2e_default --env-file $envFile -e SMARTTRAFFIC_APPLICATION_FLOW=true -e MAVEN_OPTS=-Xmx384m -v "${fixtures}:/fixtures:ro" -v "${root}:/workspace" -v "${root}/.tools/m2:/root/.m2" -w /workspace/infra/local-integration/probe maven:3.9-eclipse-temurin-17 mvn -B -ntp '-Dtest=ApplicationFlowTest' '-DargLine=-Xmx256m' test
} finally {
    Invoke-Docker @compose up -d --no-build simulator
}
