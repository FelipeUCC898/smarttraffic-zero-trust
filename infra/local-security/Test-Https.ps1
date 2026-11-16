#requires -Version 7.0
[CmdletBinding()]
param([string]$OpenSslCommand = 'openssl', [string]$MavenCommand = 'mvn')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fixture = Join-Path $root ('.tools/https-security/' + [Guid]::NewGuid())
# Disposable localhost fixtures only. Never distribute this key material to AWS.
New-Item -ItemType Directory -Force $fixture | Out-Null
function Invoke-OpenSsl {
    & $OpenSslCommand @args 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate disposable HTTPS test material' }
}
Invoke-OpenSsl req -x509 -newkey rsa:2048 -noenc -days 1 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost' -keyout "$fixture/server.key" -out "$fixture/server.pem"
Invoke-OpenSsl pkcs12 -export -name localhost -in "$fixture/server.pem" -inkey "$fixture/server.key" -out "$fixture/server.p12" -passout pass:test-only
Invoke-OpenSsl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$fixture/jwt-private.pem"
Invoke-OpenSsl pkey -in "$fixture/jwt-private.pem" -pubout -out "$fixture/jwt-public.pem"
$previous = $env:SMARTTRAFFIC_RABBIT_TEST_DIR
try {
    $env:SMARTTRAFFIC_RABBIT_TEST_DIR = $fixture
    & $MavenCommand -B -ntp "-Dmaven.repo.local=$root/.tools/m2" -f "$root/service-security/pom.xml" '-Dtest=AuthHttpsIntegrationTest' '-DargLine=-Xmx256m' test
    if ($LASTEXITCODE -ne 0) { throw 'HTTPS token integration test failed' }
} finally {
    $env:SMARTTRAFFIC_RABBIT_TEST_DIR = $previous
}
