#!/bin/bash
set -euo pipefail
umask 077
# Kafka 3.6 treats non-empty JAAS module options as a request to mint an unsecured
# client token. This listener is server-only (interbroker is mTLS), so keep JAAS
# empty and configure HTTPS JWKS trust through JSSE instead.
export KAFKA_OPTS="${KAFKA_OPTS:-} -Djavax.net.ssl.trustStore=/run/secrets/truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}"
cat > /tmp/server.properties <<EOF
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@127.0.0.1:9094
controller.listener.names=CONTROLLER
listeners=CLIENT://0.0.0.0:9093,INTERNAL://127.0.0.1:9092,CONTROLLER://127.0.0.1:9094
advertised.listeners=CLIENT://${KAFKA_HOST}:9093,INTERNAL://127.0.0.1:9092
listener.security.protocol.map=CLIENT:SASL_SSL,INTERNAL:SSL,CONTROLLER:SSL
inter.broker.listener.name=INTERNAL
ssl.keystore.type=PEM
ssl.keystore.location=/run/secrets/kafka-combined.pem
ssl.truststore.type=PEM
ssl.truststore.location=/run/secrets/ca.pem
ssl.client.auth=required
ssl.endpoint.identification.algorithm=https
listener.name.client.ssl.client.auth=none
sasl.enabled.mechanisms=OAUTHBEARER
listener.name.client.oauthbearer.sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
listener.name.client.oauthbearer.sasl.server.callback.handler.class=org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerValidatorCallbackHandler
sasl.oauthbearer.jwks.endpoint.url=${AUTH_JWKS_URL}
sasl.oauthbearer.expected.issuer=smarttraffic-auth
sasl.oauthbearer.expected.audience=smarttraffic-brokers
sasl.oauthbearer.clock.skew.seconds=0
authorizer.class.name=org.apache.kafka.metadata.authorizer.StandardAuthorizer
super.users=User:CN=kafka
allow.everyone.if.no.acl.found=false
auto.create.topics.enable=false
offsets.topic.replication.factor=1
offsets.topic.num.partitions=3
transaction.state.log.replication.factor=1
transaction.state.log.num.partitions=3
transaction.state.log.min.isr=1
default.replication.factor=1
num.partitions=1
log.dirs=/var/lib/kafka/data/logs
log.retention.hours=24
log.retention.bytes=1073741824
EOF
kafka-storage format --ignore-formatted -t MkU3OEVBNTcwNTJENDM2Qk -c /tmp/server.properties
exec kafka-server-start /tmp/server.properties
