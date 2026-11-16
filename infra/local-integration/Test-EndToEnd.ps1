[CmdletBinding()]
param([switch]$SkipBuild, [switch]$KeepRunning)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$fixtures = Join-Path $root '.tools/secure-e2e'
$envFile = Join-Path $fixtures 'runtime.env'
New-Item -ItemType Directory -Force -Path $fixtures | Out-Null
function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed ($LASTEXITCODE): $($args[0])" }
}
if (!(Test-Path $envFile)) {
    $lines = @("PROJECT_ROOT=$($root.Replace('\','/'))", "SECRETS_DIR=$($fixtures.Replace('\','/'))",
        'AUTH_URL=https://auth-server:8443', 'DATA_HOST=kafka')
    foreach ($name in @('TRUSTSTORE_PASSWORD','POSTGRES_PASSWORD','AUTH_CLIENT_TRAFFIC_SIMULATOR_SECRET',
        'AUTH_CLIENT_PROCESSOR_SECRET','AUTH_CLIENT_TRAFFIC_ARCHIVER_SECRET','AUTH_CLIENT_ALERT_DISPATCHER_SECRET',
        'AUTH_CLIENT_QUERY_CLIENT_SECRET','AUTH_CLIENT_TRAFFIC_DASHBOARD_SECRET')) {
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $lines += "$name=$([Convert]::ToHexString($bytes))"
    }
    [IO.File]::WriteAllLines($envFile, $lines)
}
$compose = @('compose','--env-file',$envFile,'-f',"$PSScriptRoot/compose.yml")
try {
    Invoke-Docker run --rm --env-file $envFile -v "${fixtures}:/certs" -v "${PSScriptRoot}/generate.sh:/generate.sh:ro" alpine:3.21 sh /generate.sh
    Invoke-Docker @compose config -q
    if (!$SkipBuild) { Invoke-Docker @compose build }
    # Start the real HTTPS issuer before Kafka fetches JWKS. No curl -k fallback.
    Invoke-Docker @compose up -d auth-server
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & docker run --rm --network smarttraffic-e2e_default -v "${fixtures}/ca.pem:/ca.pem:ro" curlimages/curl:8.12.1 --fail --silent --connect-timeout 3 --max-time 5 --cacert /ca.pem https://auth-server:8443/.well-known/jwks.json --output /dev/null 2>$null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 2
    }
    if (!$ready) { throw 'HTTPS Auth Server did not become ready' }
    Invoke-Docker @compose up -d --wait --wait-timeout 180 kafka rabbitmq postgres
    Invoke-Docker @compose exec -T kafka bash /run/config/kafka-init.sh
    Invoke-Docker run --rm --memory 1g --network smarttraffic-e2e_default --env-file $envFile -e MAVEN_OPTS=-Xmx384m -v "${fixtures}:/fixtures:ro" -v "${root}:/workspace" -v "${env:USERPROFILE}/.m2:/root/.m2" -w /workspace/infra/local-integration/probe maven:3.9-eclipse-temurin-17 mvn -B -ntp -DargLine=-Xmx256m test
    $started = [DateTime]::UtcNow.ToString('o')
    Invoke-Docker @compose up -d archiver dispatcher query dashboard
    Invoke-Docker @compose up -d processor simulator
    $passed = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $rows = & docker @compose exec -T postgres psql -U smarttraffic -d smarttraffic -Atc "SELECT count(*) FROM traffic_observation WHERE sensor_id='secure-e2e-sensor' AND zone_id='north' AND vehicle_count=42 AND measured_at >= '$started';" 2>$null
        if ($LASTEXITCODE -eq 0 -and [int]($rows | Select-Object -Last 1) -gt 0) {
            try {
                $answer = Invoke-RestMethod 'http://127.0.0.1:18092/api/traffic/north' -TimeoutSec 8
                if ($answer.status -eq 'OK' -and [DateTime]$answer.state.measuredAt -ge [DateTime]$started) { $passed=$true; break }
            } catch { }
        }
        Start-Sleep -Seconds 3
    }
    if (!$passed) { throw 'End-to-end timeout: PostgreSQL persistence and live RabbitMQ query were not both confirmed' }
    Write-Host "PASS: HTTPS token + Kafka SASL_SSL/JWKS/ACL + PostgreSQL verify-full: $rows rows. RabbitMQ OAuth2 query: $($answer.status)."
} finally {
    if (!$KeepRunning) { Invoke-Docker @compose stop }
}
