#!/bin/sh
set -eu
# PostgreSQL refuses world-readable private keys, including Windows bind mounts.
install -o postgres -g postgres -m 600 /run/secrets/postgres.key /tmp/postgres.key
exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/run/secrets/postgres.pem -c ssl_key_file=/tmp/postgres.key -c hba_file=/run/config/pg_hba.conf
