[CmdletBinding()]
param(
    [uri]$QueryUri = 'http://127.0.0.1:8092/api/traffic/north',
    [ValidateRange(1,3600)][int]$MaximumAgeSeconds = 30,
    [DateTimeOffset]$NotBefore = [DateTimeOffset]::MinValue
)
$ErrorActionPreference='Stop'
$answer=Invoke-RestMethod -Uri $QueryUri -TimeoutSec 10
$requestId=[Guid]::Empty
if($answer.status -ne 'OK' -or ![Guid]::TryParse([string]$answer.requestId,[ref]$requestId) -or !$answer.state.measuredAt) {
    throw 'Query did not return a correlated OK response with observed state.'
}
$measured=[DateTimeOffset]::Parse($answer.state.measuredAt)
$age=([DateTimeOffset]::UtcNow-$measured).TotalSeconds
if($age -gt $MaximumAgeSeconds -or $age -lt -5 -or $measured -lt $NotBefore) {
    throw "Query returned stale/future state (age=$([Math]::Round($age,2)) seconds). Container status or OK alone is insufficient."
}
[pscustomobject]@{Status=$answer.status;RequestId=$answer.requestId;MeasuredAt=$measured.ToString('o');AgeSeconds=[Math]::Round($age,2);State=$answer.state.congestionState} | ConvertTo-Json
