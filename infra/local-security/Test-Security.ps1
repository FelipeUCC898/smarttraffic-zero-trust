[CmdletBinding()]
param([string]$MavenCommand = 'mvn')
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixtures = Join-Path $repo '.tools/rabbit-security'
$compose = Join-Path $PSScriptRoot 'compose.yml'
New-Item -ItemType Directory -Force $fixtures | Out-Null
& docker run --rm -v "${fixtures}:/certs" -v "${PSScriptRoot}/generate.sh:/generate.sh:ro" alpine:3.21 sh /generate.sh
if ($LASTEXITCODE -ne 0) { throw 'Could not generate local test certificates' }
$previous = $env:SMARTTRAFFIC_RABBIT_TEST_DIR
try {
    & docker compose -p smarttraffic-security-test -f $compose up -d --wait --wait-timeout 120
    if ($LASTEXITCODE -ne 0) { throw 'Local secure RabbitMQ did not become healthy' }
    $env:SMARTTRAFFIC_RABBIT_TEST_DIR = $fixtures
    & $MavenCommand -q -f (Join-Path $repo 'auth-server/pom.xml') '-Dtest=RabbitSecurityIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ security integration tests failed' }
    & $MavenCommand -q -f (Join-Path $repo 'service-security/pom.xml') '-Dtest=AuthHttpsIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { throw 'Auth HTTPS integration test failed' }
} finally {
    $env:SMARTTRAFFIC_RABBIT_TEST_DIR = $previous
    & docker compose -p smarttraffic-security-test -f $compose stop
}
