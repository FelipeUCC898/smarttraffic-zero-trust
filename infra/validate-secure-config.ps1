[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$CertsDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($EnvFile)) { $EnvFile = Join-Path $repoRoot '.env' }
$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "No existe el archivo de entorno: $EnvFile"
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $values[$Matches[1]] = $Matches[2].Trim()
    }
}

function Require-Value([string]$name) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        $errors.Add("Falta $name")
        return $false
    }
    $template = $values[$name]
    if ($template -match 'example\.internal|REPLACE_|HOST_REAL|HOST_KAFKA_REAL|/ruta/|changeme') {
        $errors.Add("$name todavía contiene un valor de plantilla")
        return $false
    }
    return $true
}

foreach ($name in @(
    'SECURE_AUTH_TOKEN_ENDPOINT', 'SECURE_KAFKA_BOOTSTRAP_SERVERS',
    'SECURE_CERTS_DIR', 'AUTH_CA_CERTIFICATE', 'KAFKA_TRUSTSTORE',
    'KAFKA_TRUSTSTORE_PASSWORD', 'AUTH_CLIENT_TRAFFIC_ARCHIVER_SECRET'
)) { [void](Require-Value $name) }

if ($values.ContainsKey('SECURE_AUTH_TOKEN_ENDPOINT')) {
    try {
        $uri = [Uri]$values.SECURE_AUTH_TOKEN_ENDPOINT
        if ($uri.Scheme -ne 'https' -or $uri.AbsolutePath -ne '/token') {
            $errors.Add('SECURE_AUTH_TOKEN_ENDPOINT debe usar HTTPS y terminar exactamente en /token')
        }
    } catch { $errors.Add('SECURE_AUTH_TOKEN_ENDPOINT no es una URL válida') }
}

if ($values.ContainsKey('SECURE_KAFKA_BOOTSTRAP_SERVERS') -and
    $values.SECURE_KAFKA_BOOTSTRAP_SERVERS -notmatch '^[^,:\s]+:\d{1,5}$') {
    $errors.Add('SECURE_KAFKA_BOOTSTRAP_SERVERS debe tener el formato host:puerto')
}

if (-not $CertsDirectory -and $values.ContainsKey('SECURE_CERTS_DIR')) {
    $CertsDirectory = $values.SECURE_CERTS_DIR
}
if ($CertsDirectory -and -not (Test-Path -LiteralPath $CertsDirectory -PathType Container)) {
    $errors.Add("No existe SECURE_CERTS_DIR: $CertsDirectory")
}

if ($CertsDirectory -and (Test-Path -LiteralPath $CertsDirectory -PathType Container)) {
    foreach ($name in @('AUTH_CA_CERTIFICATE', 'KAFKA_TRUSTSTORE')) {
        if ($values.ContainsKey($name)) {
            $containerPath = $values[$name]
            $fileName = Split-Path $containerPath -Leaf
            if (-not (Test-Path -LiteralPath (Join-Path $CertsDirectory $fileName) -PathType Leaf)) {
                $errors.Add("No se encontró $fileName dentro de SECURE_CERTS_DIR")
            }
        }
    }
}

$ignore = git -C $repoRoot check-ignore .env 2>$null
if (-not $ignore) { $errors.Add('.env no está siendo ignorado por Git') }

Write-Host 'Preflight de configuración segura SmartTraffic'
Write-Host "Archivo evaluado: $EnvFile"
Write-Host "Certificados evaluados: $(if ($CertsDirectory) { $CertsDirectory } else { '<no indicado>' })"
Write-Host "Variables sensibles presentes: $($values.ContainsKey('AUTH_CLIENT_TRAFFIC_ARCHIVER_SECRET'))"

if ($warnings.Count -gt 0) {
    Write-Host 'Advertencias:'
    $warnings | ForEach-Object { Write-Host "- $_" }
}
if ($errors.Count -gt 0) {
    Write-Host 'FALLOS:'
    $errors | ForEach-Object { Write-Host "- $_" }
    exit 1
}

Write-Host 'OK: formato, rutas locales y exclusión de .env validados.'
Write-Host 'Pendiente de confirmación externa: registro de traffic-archiver y permiso de consumo sobre raw_traffic_data con traffic-archiver-v1.'
