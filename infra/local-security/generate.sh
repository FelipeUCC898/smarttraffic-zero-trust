#!/bin/sh
set -eu
apk add --no-cache openssl >/dev/null
cd /certs
# Disposable local-only fixtures. Refuse to overwrite keys from an earlier run.
if [ -f jwt-private.pem ]; then
  openssl x509 -in server.pem -checkend 300 -noout >/dev/null || { echo 'Test certificate expired; regenerate disposable fixtures in a new directory.'; exit 1; }
  openssl pkcs12 -export -in server.pem -inkey server.key -out server.p12 -passout pass:test-only
  exit 0
fi
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem 2>/dev/null
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
openssl req -x509 -newkey rsa:2048 -noenc -sha256 -days 2 -subj /CN=localhost -addext subjectAltName=DNS:localhost -keyout server.key -out server.pem 2>/dev/null
# Test-only server key must be readable by the RabbitMQ image's non-root UID.
chmod 644 server.key server.pem jwt-public.pem
openssl pkcs12 -export -in server.pem -inkey server.key -out server.p12 -passout pass:test-only
