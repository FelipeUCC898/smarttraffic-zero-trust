[CmdletBinding()]
param(
    [DateTimeOffset]$From = [DateTimeOffset]::Now,
    [DateTimeOffset]$Until = [DateTimeOffset]'2026-11-23T00:00:00-05:00',
    [double]$RunningHours = -1,
    [double]$ExtraTransferGiB = 0,
    [double]$TransferUsdPerGiB = 0.09
)
$ErrorActionPreference = 'Stop'
$diskHours = ($Until - $From).TotalHours
if ($diskHours -le 0) { throw 'Cutoff must be later than the start.' }
if ($RunningHours -eq -1) { $RunningHours = $diskHours }
if ($RunningHours -lt 0 -or $RunningHours -gt $diskHours -or $ExtraTransferGiB -lt 0 -or $TransferUsdPerGiB -lt 0) { throw 'Invalid usage estimate.' }
# Ohio Price List snapshot in evidence/free-plan-review. No AWS calls.
$computeIpv4 = 0.26795 * $RunningHours
$ebs = 9.60 * $diskHours / 730
$transfer = $ExtraTransferGiB * $TransferUsdPerGiB
[pscustomobject]@{
    From = $From.ToString('o'); Until = $Until.ToString('o')
    RunningHours = [math]::Round($RunningHours, 3)
    DiskHours = [math]::Round($diskHours, 3)
    ComputeAndIPv4Usd = [math]::Round($computeIpv4, 4)
    EbsUsd = [math]::Round($ebs, 4)
    TransferScenarioUsd = [math]::Round($transfer, 4)
    BaseTotalUsd = [math]::Round($computeIpv4 + $ebs + $transfer, 4)
    Excludes = 'Taxes, other services/account usage, credit expiry/sharing/coverage. Not a spending cap.'
} | ConvertTo-Json
