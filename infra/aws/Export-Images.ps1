#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('auth','data','ingest','core','app')][string]$Zone,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$definition = Get-Content "$PSScriptRoot/zones/$Zone.yml" -Raw | ConvertFrom-Json -AsHashtable
$images = @($definition.services.Values.image | Sort-Object -Unique)
$manifest = foreach ($reference in $images) {
    # Do not export image environment/configuration, which can contain credentials.
    $raw = & docker image inspect --format '{{json .Id}} {{json .Os}} {{json .Architecture}}' $reference
    if ($LASTEXITCODE -ne 0) { throw "Image missing locally: $reference. Build/pull explicitly first." }
    $parts = $raw.Trim() -split ' '
    $id, $os, $arch = $parts | ForEach-Object { $_ | ConvertFrom-Json }
    if ($id -notmatch '^sha256:[a-f0-9]{64}$' -or $os -ne 'linux' -or $arch -ne 'amd64') {
        throw "Expected a verified Linux/amd64 image: $reference"
    }
    "$id $reference"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$output = (Resolve-Path $OutputDirectory).Path
$archive = Join-Path $output "$Zone-images.tar"
if (Test-Path $archive) { throw "Refusing to overwrite an existing bundle: $archive" }
& docker image save --output $archive @images
if ($LASTEXITCODE -ne 0) { throw 'Image export failed; do not distribute a partial bundle.' }
$manifestPath = Join-Path $output "$Zone-imageids.txt"
# This is a Linux interchange format. WriteAllLines uses CRLF on Windows,
# which sha256sum/read treat as part of the filename/reference on the VM.
[IO.File]::WriteAllText($manifestPath, (($manifest -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
$hashes = foreach ($file in @($archive, $manifestPath)) {
    "$((Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($file))"
}
[IO.File]::WriteAllText((Join-Path $output "$Zone-SHA256SUMS"), (($hashes -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
Write-Output "Exported ${Zone}: $($images.Count) Linux/amd64 images. Review image IDs and deliver through an approved channel."
