[CmdletBinding()]
param([Parameter(Mandatory)] [string]$FixtureEnv)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$catalog=Get-Content "$root/infra/runtime/services.yml" -Raw | ConvertFrom-Json -AsHashtable
$membership=@{auth=@('auth-server');data=@('kafka','rabbitmq','postgres');ingest=@('simulator');core=@('processor','archiver');app=@('dispatcher','query','dashboard')}
$envLines=Get-Content $FixtureEnv
$scratch=Join-Path $root '.tools/zone-validation'
New-Item -ItemType Directory -Force $scratch | Out-Null
foreach($zone in $membership.Keys) {
    $file="$PSScriptRoot/zones/$zone.yml"
    $definition=Get-Content $file -Raw | ConvertFrom-Json -AsHashtable
    if(@(Compare-Object ($membership[$zone] | Sort-Object) ($definition.services.Keys | Sort-Object)).Count) { throw "Incorrect services: $zone" }
    foreach($name in $membership[$zone]) {
        $expected=$catalog.services[$name] | ConvertTo-Json -Depth 40 | ConvertFrom-Json -AsHashtable
        $expected.Remove('build')
        $expected.network_mode='host'
        if($name -in @('query','dashboard')) { $expected.environment.SERVER_ADDRESS='127.0.0.1' }
        if($name -eq 'dashboard') { $expected.environment.DASHBOARD_BIND_ADDRESS='127.0.0.1' }
        # Normalize property ordering by comparing each top-level value recursively
        # through compressed JSON from the same ordered source catalog.
        $actual=$definition.services[$name]
        if(($expected | ConvertTo-Json -Depth 40 -Compress) -cne ($actual | ConvertTo-Json -Depth 40 -Compress)) { throw "Zone drift: $zone/$name" }
    }
    $text=Get-Content $file -Raw
    $required=[regex]::Matches($text,'\$\{([A-Z0-9_]+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
    $filtered=$envLines | Where-Object { ($_ -split '=',2)[0] -in $required }
    $path=Join-Path $scratch "$zone.env"
    [IO.File]::WriteAllLines($path,$filtered)
    & docker compose --env-file $path -f $file config -q
    if($LASTEXITCODE -ne 0) { throw "Compose validation failed: $zone" }
    Write-Host "PASS $zone (only its required environment variables)"
}
