#!/bin/bash
set -euo pipefail
admin=(--bootstrap-server 127.0.0.1:9092 --command-config /run/config/kafka-admin.properties)
kafka-topics "${admin[@]}" --create --if-not-exists --topic raw_traffic_data --partitions 1 --replication-factor 1
acl() { kafka-acls "${admin[@]}" --add --allow-principal "User:$1" "${@:2}"; }
acl traffic-simulator --operation Write --operation Describe --topic raw_traffic_data
acl traffic-simulator --operation IdempotentWrite --cluster
acl traffic-archiver --operation Read --operation Describe --topic raw_traffic_data
acl traffic-archiver --operation Read --group traffic-archiver-v1
acl processor --operation Read --operation Describe --topic raw_traffic_data
# Kafka Streams' local state changelogs and EOS transaction IDs belong to this app.
acl processor --operation All --topic density-processor-demo- --resource-pattern-type prefixed
acl processor --operation Read --group density-processor-demo
acl processor --operation Write --operation Describe --transactional-id density-processor-demo --resource-pattern-type prefixed
acl processor --operation IdempotentWrite --cluster
