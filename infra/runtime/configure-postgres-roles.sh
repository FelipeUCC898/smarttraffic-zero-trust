#!/bin/sh
set -eu
# Explicit administrative step, not automatic initdb or password rotation.
# Run only on the chosen lab database. Passwords enter through root-only files.
export MIGRATOR_PASSWORD="$(tr -d '\r\n' < /run/secrets/migrator-password)"
export ARCHIVER_PASSWORD="$(tr -d '\r\n' < /run/secrets/archiver-password)"
test -n "$MIGRATOR_PASSWORD" && test -n "$ARCHIVER_PASSWORD"
psql -X --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv migrator_password MIGRATOR_PASSWORD
\getenv archiver_password ARCHIVER_PASSWORD
BEGIN;
SELECT 'CREATE ROLE smarttraffic_migrator' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='smarttraffic_migrator') \gexec
SELECT 'CREATE ROLE smarttraffic_archiver' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='smarttraffic_archiver') \gexec
SELECT format('ALTER ROLE smarttraffic_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'migrator_password') \gexec
SELECT format('ALTER ROLE smarttraffic_archiver LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'archiver_password') \gexec
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO smarttraffic_migrator;
GRANT USAGE ON SCHEMA public TO smarttraffic_archiver;
SELECT format('GRANT CONNECT ON DATABASE %I TO smarttraffic_migrator, smarttraffic_archiver', current_database()) \gexec
SELECT 'ALTER TABLE public.traffic_observation OWNER TO smarttraffic_migrator' WHERE to_regclass('public.traffic_observation') IS NOT NULL \gexec
SELECT 'ALTER TABLE public.flyway_schema_history OWNER TO smarttraffic_migrator' WHERE to_regclass('public.flyway_schema_history') IS NOT NULL \gexec
SELECT 'GRANT SELECT, INSERT ON TABLE public.traffic_observation TO smarttraffic_archiver' WHERE to_regclass('public.traffic_observation') IS NOT NULL \gexec
COMMIT;
SQL
unset MIGRATOR_PASSWORD ARCHIVER_PASSWORD
