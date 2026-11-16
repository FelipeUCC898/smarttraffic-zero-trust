#!/bin/bash
set -euo pipefail
# On the approved destination VM only. Install an inspected per-zone directory.
test "$(id -u)" = 0 || { echo 'Run as root on the selected VM' >&2; exit 1; }
zone=$1
source_dir=$(realpath "$2")
case "$zone" in auth|data|ingest|core|app) ;; *) exit 1;; esac
test "$(hostname)" = "vm-$zone" || { echo 'Wrong VM hostname' >&2; exit 1; }
test ! -e /opt/smarttraffic/runtime.env || { echo 'Existing installation: review update/rollback explicitly' >&2; exit 1; }
test -f "$source_dir/repo/infra/aws/zones/$zone.yml"
install -d -m 0700 /opt/smarttraffic/secrets
install -d -m 0755 /opt/smarttraffic/repo
cp -R "$source_dir/repo/infra" /opt/smarttraffic/repo/
find /opt/smarttraffic/repo/infra -type d -exec chmod 0755 {} +
find /opt/smarttraffic/repo/infra -type f -exec chmod 0644 {} +
install -m 0600 "$source_dir/runtime.env" /opt/smarttraffic/runtime.env
for name in ca.pem jwt-public.pem truststore.p12 kafka-combined.pem rabbitmq.pem rabbitmq.key postgres.pem postgres.key auth-server.p12 jwt-private.pem migrator-password archiver-password; do
  test -f "$source_dir/secrets/$name" || continue
  uid=0
  mode=0600
  case "$name" in
    ca.pem|jwt-public.pem|truststore.p12|rabbitmq.pem|postgres.pem) mode=0444;;
    auth-server.p12|jwt-private.pem)
      uid=$(docker run --rm --network none --entrypoint id smarttraffic/auth-server:aws-lab -u);;
    kafka-combined.pem)
      uid=$(docker run --rm --network none --entrypoint id confluentinc/cp-kafka:7.6.1 -u);;
    rabbitmq.key)
      uid=$(docker run --rm --network none --entrypoint id rabbitmq:3.13-alpine -u rabbitmq);;
  esac
  install -o "$uid" -g 0 -m "$mode" "$source_dir/secrets/$name" "/opt/smarttraffic/secrets/$name"
done
cd /opt/smarttraffic/repo
files=(-f "infra/aws/zones/$zone.yml")
test ! -f "infra/aws/zones/$zone-roles.yml" || files+=(-f "infra/aws/zones/$zone-roles.yml")
docker compose --env-file /opt/smarttraffic/runtime.env "${files[@]}" config -q
echo 'Installed filtered configuration. No application services started.'
