$ErrorActionPreference = 'Stop'

if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    throw 'OpenSSL no está instalado. Instálalo en la máquina de desarrollo y vuelve a ejecutar este script.'
}

$out = Join-Path $PSScriptRoot 'generated'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function New-Certificate([string]$name, [string[]]$sans) {
    $key = Join-Path $out "$name.key"
    $csr = Join-Path $out "$name.csr"
    $crt = Join-Path $out "$name.crt"
    $config = Join-Path $out "$name.cnf"
    $sanLines = ($sans | ForEach-Object { "DNS.$([array]::IndexOf($sans, $_) + 1) = $_" }) -join "`n"
    @"
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = $name
[v3_req]
subjectAltName = @alt_names
[alt_names]
$sanLines
"@ | Set-Content -Encoding ascii $config
    openssl req -new -newkey rsa:3072 -nodes -keyout $key -out $csr -config $config
    openssl x509 -req -in $csr -CA (Join-Path $out 'dev-ca.crt') -CAkey (Join-Path $out 'dev-ca.key') -CAcreateserial -out $crt -days 90 -sha256 -extfile $config -extensions v3_req
    Remove-Item $csr, $config -Force
}

openssl req -x509 -newkey rsa:4096 -nodes -keyout (Join-Path $out 'dev-ca.key') -out (Join-Path $out 'dev-ca.crt') -days 365 -subj '/CN=SmartTraffic Development CA'
New-Certificate 'auth-server' @('auth-server', 'localhost')
New-Certificate 'kafka' @('kafka', 'localhost')
New-Certificate 'rabbitmq' @('rabbitmq', 'localhost')
Write-Host "Certificados de desarrollo creados en $out. No los subas al repositorio."
