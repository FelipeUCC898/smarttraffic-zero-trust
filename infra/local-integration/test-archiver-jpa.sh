#!/bin/sh
set -eu
export SMARTTRAFFIC_POSTGRES_TEST=true
export SPRING_PROFILES_ACTIVE=secure-integration
export SECURE_AUTH_TOKEN_ENDPOINT=https://auth-server:8443/token
export SECURE_KAFKA_BOOTSTRAP_SERVERS=kafka:9093
export AUTH_CLIENT_ID=traffic-archiver
export AUTH_CLIENT_SECRET="$AUTH_CLIENT_TRAFFIC_ARCHIVER_SECRET"
export AUTH_CA_CERTIFICATE=/fixtures/ca.pem
export POSTGRES_HOST=postgres POSTGRES_DB=smarttraffic POSTGRES_USER=smarttraffic_archiver
export POSTGRES_PASSWORD="$ARCHIVER_DB_PASSWORD"
export SPRING_FLYWAY_USER=smarttraffic_migrator
export SPRING_FLYWAY_PASSWORD="$MIGRATOR_DB_PASSWORD"
export SPRING_DATASOURCE_URL='jdbc:postgresql://postgres:5432/smarttraffic?sslmode=verify-full&sslrootcert=/fixtures/ca.pem'
exec mvn -B -ntp -f /workspace/traffic-archiver/pom.xml -Dtest=PostgreSqlConnectionTest -DargLine=-Xmx256m test
