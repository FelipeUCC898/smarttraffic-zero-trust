# Reproducible secure local flow

Requires Docker Desktop and PowerShell 7. Builds the actual Java 17 services, uses
an isolated `smarttraffic-e2e` project, and stops its containers in `finally`.
It does not use AWS APIs, create cloud resources, alter the default local Compose
stack, or remove volumes.

Current workstation evidence, PostgreSQL roles and deployment preparation are in
[RELEASE-READINESS.md](../aws/RELEASE-READINESS.md). The issuer in this local
Compose uses **60-second JWTs** to expose renewal failures; deployment keeps its
normal 900-second lifetime. Initial dependency downloads or a suspended/overloaded
workstation can interrupt this short-lived test. An old OK query is insufficient:
the probes require fresh timestamps. Images now build sequentially by service.

Additional checks, after the baseline succeeds (PowerShell 7):

```powershell
./infra/local-integration/Test-EndToEnd.ps1 -KeepRunning
./infra/local-integration/Test-ApplicationFlow.ps1
./infra/local-integration/Test-PostgresRoles.ps1
./infra/local-integration/Test-Recovery.ps1
./infra/local-security/Test-Security.ps1
```

ApplicationFlow pauses only its own simulator, publishes controlled transitions,
checks a real alert consumer/WebSocket and waits beyond token expiry. PostgresRoles
adds separate credentials to the existing ignored fixture env, provisions roles,
rebuilds only archiver and checks fresh persistence. It retains a dedicated tiny
fresh-schema test database. Recovery uses the roles overlays and keeps volumes.
Use those same overlays when operating this local stack after the role test:

```powershell
docker compose --env-file .tools/secure-e2e/runtime.env -f infra/local-integration/compose.yml -f infra/aws/zones/data-roles.yml -f infra/aws/zones/core-roles.yml stop -t 60
```

Do not switch the role-enabled archiver back to bootstrap by accidentally omitting
the overlays. No fixture from either harness is suitable for AWS delivery.

Run only one SmartTraffic stack at a time on memory-constrained Docker Desktop.
The test machine exposes about 3.5 GiB to Docker: running the old development stack
alongside this full secure stack caused severe memory pressure, slow token requests
and Kafka coordination timeouts. Stop the previous stack before testing and restore
it afterwards; the harness deliberately does not stop unrelated containers.

```powershell
./infra/local-integration/Test-EndToEnd.ps1
# After the first build (only if application code/Dockerfiles have not changed):
./infra/local-integration/Test-EndToEnd.ps1 -SkipBuild
```

The harness generates random local client/database passwords, a disposable CA,
server certificates, PKCS12 truststore and an RS256 signing key under ignored
`.tools/secure-e2e`. Certificates expire after two days and expired fixtures fail
closed. Regenerate certificates with the existing credentials, or reset this
disposable project and move its old fixture directory aside before another run.
Fresh database passwords require fresh disposable volumes; do not mix new
credentials with an existing database volume. They are not deployment credentials.
Only the test fixture generator/probe sees the entire fixture directory; running
application containers receive individual files appropriate to their roles.

Execution order:

1. Real Auth Server serves HTTPS `/token` and `/.well-known/jwks.json`.
2. Kafka fetches HTTPS JWKS and enables RS256 validation and default-deny ACLs.
   Internal/control listeners use mutual TLS and bind to loopback. RabbitMQ
   accepts OAuth2 over TLS only. PostgreSQL rejects TCP plaintext.
3. A separate Java probe tests allowed writes, denied topics/identities, bad
   signature/issuer/audience/expiry, untrusted CA, wrong hostname and PostgreSQL
   TLS enforcement. A successful security probe publishes an explicitly named
   `security-probe` synthetic event; it is excluded from persistence assertions.
4. The real simulator publishes `secure-e2e-sensor` observations. The real archiver
   consumes through SASL_SSL and migrates/persists into PostgreSQL with
   `sslmode=verify-full`. The assertion requires rows newer than this test run.
5. The real processor runs Kafka Streams with persistent local state and sends
   updates to RabbitMQ. Dispatcher stores current state; query-client retrieves
   that state through an exclusive reply queue. The assertion requires an OK
   reply containing a state newer than the test run. The HTTP test endpoint is
   exposed on localhost only at 18092.

Optional `-KeepRunning` is for debugging and explicitly leaves this local stack
running. Afterwards stop it with:

```powershell
docker compose --env-file .tools/secure-e2e/runtime.env -f infra/local-integration/compose.yml stop
```

This is single-node functional/security validation, not a cloud network test,
soak test, load test or failover test. WebSocket/alert delivery is checked by the
separate ApplicationFlow probe, not the baseline assertion. No real
road graph is supplied. Deployment requirements are listed in
[`../aws/zones/README.md`](../aws/zones/README.md).

Broker implementation reference: [Kafka OAuthBearer validator](https://kafka.apache.org/36/javadoc/org/apache/kafka/common/security/oauthbearer/secured/OAuthBearerValidatorCallbackHandler.html).
