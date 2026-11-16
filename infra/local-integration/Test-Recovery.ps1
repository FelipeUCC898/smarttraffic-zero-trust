#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$compose=@('compose','--env-file',"$root/.tools/secure-e2e/runtime.env",'-f',"$PSScriptRoot/compose.yml",'-f',"$root/infra/aws/zones/data-roles.yml",'-f',"$root/infra/aws/zones/core-roles.yml")
function Invoke-Docker {
    $result=& docker @args
    if($LASTEXITCODE -ne 0) { throw "Recovery command failed: $($args[0])" }
    return $result
}
$before=[int](Invoke-Docker @compose exec -T postgres psql -U smarttraffic -d smarttraffic -Atc 'SELECT count(*) FROM traffic_observation;' | Select-Object -Last 1)
Invoke-Docker @compose stop -t 60 simulator processor archiver dispatcher query dashboard
Invoke-Docker @compose restart kafka rabbitmq postgres
Invoke-Docker @compose up -d --no-build --wait --wait-timeout 180 kafka rabbitmq postgres
$retained=[int](Invoke-Docker @compose exec -T postgres psql -U smarttraffic -d smarttraffic -Atc 'SELECT count(*) FROM traffic_observation;' | Select-Object -Last 1)
if($retained -lt $before) { throw 'Previously persisted rows were lost' }
Invoke-Docker @compose up -d --no-build dispatcher query dashboard
Invoke-Docker @compose up -d --no-build processor archiver
$started=[DateTimeOffset]::UtcNow
Invoke-Docker @compose up -d --no-build simulator
$passed=$false
for($i=0;$i -lt 60;$i++) {
    $after=[int](Invoke-Docker @compose exec -T postgres psql -U smarttraffic -d smarttraffic -Atc 'SELECT count(*) FROM traffic_observation;' | Select-Object -Last 1)
    try {
        $answer=Invoke-RestMethod 'http://127.0.0.1:18092/api/traffic/north' -TimeoutSec 8
        if($after -gt $retained -and $answer.status -eq 'OK' -and [DateTimeOffset]$answer.state.measuredAt -ge $started) { $passed=$true;break }
    } catch {}
    Start-Sleep -Seconds 3
}
if(!$passed) { throw 'Recovery did not produce BOTH new DB rows and fresh correlated state' }
[pscustomobject]@{Before=$before;Retained=$retained;After=$after;QueryStatus=$answer.status;RequestId=$answer.requestId;MeasuredAt=$answer.state.measuredAt} | ConvertTo-Json
Write-Output 'PASS: retained PostgreSQL data, restarted brokers with existing volumes, and resumed fresh end-to-end traffic.'
