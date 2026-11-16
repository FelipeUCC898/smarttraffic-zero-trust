#!/bin/sh
set -eu
apk add --no-cache openssl openjdk17-jre-headless >/dev/null
cd /certs
umask 077
if [ -f ready ]; then
  openssl x509 -in auth-server.pem -checkend 600 -noout >/dev/null
  exit 0
fi
# These fixtures are strictly disposable; never copy this CA to AWS.
openssl req -x509 -newkey rsa:2048 -noenc -days 2 -subj /CN=SmartTraffic-local-test-CA -keyout ca.key -out ca.pem 2>/dev/null
for name in auth-server kafka rabbitmq postgres; do
  case "$name" in
    auth-server) san='DNS:auth-server,DNS:localhost,IP:127.0.0.1,IP:10.42.10.10';;
    kafka) san='DNS:kafka,DNS:data-kafka,DNS:localhost,IP:127.0.0.1,IP:10.42.10.11';;
    rabbitmq) san='DNS:rabbitmq,DNS:localhost,IP:127.0.0.1,IP:10.42.10.11';;
    postgres) san='DNS:postgres,DNS:localhost,IP:127.0.0.1,IP:10.42.10.11';;
  esac
  openssl req -new -newkey rsa:2048 -noenc -subj "/CN=$name" -keyout "$name.key" -out "$name.csr" 2>/dev/null
  printf 'subjectAltName=%s\nextendedKeyUsage=serverAuth,clientAuth\n' "$san" > "$name.ext"
  openssl x509 -req -in "$name.csr" -CA ca.pem -CAkey ca.key -CAcreateserial -days 2 -extfile "$name.ext" -out "$name.pem" 2>/dev/null
done
cat kafka.key kafka.pem > kafka-combined.pem
openssl pkcs12 -export -name auth-server -in auth-server.pem -inkey auth-server.key -certfile ca.pem -out auth-server.p12 -passout env:TRUSTSTORE_PASSWORD
keytool -importcert -noprompt -alias ca -file ca.pem -keystore truststore.p12 -storetype PKCS12 -storepass:env TRUSTSTORE_PASSWORD >/dev/null 2>&1
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem 2>/dev/null
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
# Different container UIDs need access to local fixtures. Each receives ONLY its
# own mounted private key; the fixture directory/CA key is never mounted wholesale.
chmod 644 *.pem rabbitmq.key postgres.key *.p12
touch ready
