# Preparation validation — 2026-11-16

Current local integration, roles, delivery and recovery evidence is in
[RELEASE-READINESS.md](RELEASE-READINESS.md). The results below are historical.

Historical evidence from the preceding workstation/session. Current revalidation,
missing AWS access and all unverified items are recorded in [READINESS.md](READINESS.md).
Do not treat the authenticated identity, balance or plan below as current evidence.

User scope: prepare and validate only. No AWS resources were applied or started.

## Passed

- AWS STS authenticated the existing `smarttraffic-project` session in account
  `223455088753`; regional inventory in `us-east-2` returned no EC2 instances.
- `origin/develop` fetched; integration branch created from merge `84d71a7`.
- Terraform 1.13.5 initialized with AWS provider 6.73.0; provider lockfile retained.
- Terraform format and validate succeeded.
- Authenticated Terraform plan succeeded: **53 additions, 0 changes, 0 deletions**.
  Includes five EC2 instances; the plan has not been applied.
- PowerShell helper parsed without syntax errors.
- Docker Compose configuration with `.env.example` and `secure-integration` passed.
- `git diff --check` passed.

`lab.tfplan` remains local and ignored. State/plan ignore rules were checked.
`install.ps1` was already untracked and was not modified.

## Not exercised

EC2 launch permissions/capacity, bootstrap execution, host nftables enforcement,
SSM registration and cloud certificate delivery have not been tested. Terraform
planning cannot establish these. Local Kafka/application results are recorded below.
The deployment gates in README.md remain open; this is not application sign-off.

## Application findings and current resolution

- `docker-compose.yml`: corrected query-client-secure to use the same
  `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_VHOST` variables as the secure template.
- Replaced the generic token scope with per-service RabbitMQ resource permissions,
  including routing-key permissions on the topic exchange. Kafka-only identities
  have no RabbitMQ scopes. Tested against the actual RabbitMQ OAuth2 backend.
- Registered `traffic-dashboard` and imported the shared JWT connection factory.
  A Spring context test verifies factory selection and rejects disabled broker TLS.
- Each query replica now declares an exclusive auto-delete queue with a UUID.
  Replies use the request's reply address and original correlation ID. Verified
  actual topic routing between two independent TLS/OAuth2 broker connections.
- Dispatcher queries now read the latest observed state, reject stale updates and
  avoid duplicate congestion alerts. Removed the fabricated five-minute ETA.
  Different-zone routes return `ROUTE_UNAVAILABLE`: no road graph/travel-time model
  exists yet. Full route planning and the workshop's Observer design remain open.

## Local security verification

- Java 17 module suites passed: service-security, auth-server, alert-dispatcher,
  query-client and traffic-dashboard. The Windows Java 25/Mockito incompatibility
  was avoided by using the project's Java 17 Docker toolchain.
- Eight real RabbitMQ tests passed: allowed/denied resources, wrong audience,
  expired token, hostname mismatch, untrusted CA, guest/tampered signature, and
  reply isolation between replicas.
- HTTPS AuthTokenClient test passed with a private CA and rejected the same server
  without trusting that CA. Unit tests reject wrong signature, issuer, audience,
  subject and expiry, and reject plaintext token endpoints before sending secrets.
- RS256 signing no longer needs an HMAC secret; explicit algorithm/key ID and JWKS
  unsigned modulus encoding are tested. PEM strings work on Windows without being
  interpreted as filesystem paths.
- Added an isolated reproducible harness under `infra/local-security`; its test
  broker is stopped after execution. Fixture keys remain under ignored `.tools`.
- Executed the complete harness successfully, including its final broker stop.
  Clean dashboard Docker build passed with compiled targets and sensitive files
  excluded from the context. Compose configuration and diff whitespace checks passed.

RabbitMQ uses static public-key pinning. Cloud JWKS discovery/rotation and
end-to-end application deployment on EC2 have not been exercised.

## Secure Kafka and VM runtime follow-up

- Added an isolated full application harness in `infra/local-integration` and a
  shared service catalog in `infra/runtime`.
- Kafka KRaft validates OAuth2 JWT signatures through Auth Server HTTPS JWKS,
  issuer and audience, with default-deny ACLs. Administration/controller listeners
  bind to loopback and require mutual TLS. No unsecured JWT validator is used.
- Ten real broker/database tests passed: authorized Kafka writes, denied topics
  and identities, wrong issuer/audience/signature/expiry, untrusted CA, hostname
  mismatch, and PostgreSQL verified TLS/plaintext rejection.
- Added independent auth/data/ingest/core/app Compose files with host networking,
  persistent volumes, CPU/memory limits and individual secret mounts. Each passed
  validation using only its own required environment variables. A consistency
  check compares every VM service with the locally tested service catalog.
- Fixed dispatcher CA property nesting, RabbitMQ truststore resource paths,
  processor state-directory ownership and archiver Kafka callback visibility.
  A regression test proves Kafka's reflection helper can instantiate the callback.
- Restricted metadata access to root in host nftables; IMDS hop limit alone cannot
  isolate host-network containers. Actual VM firewall enforcement remains untested.
- Terraform validate passed after the local test stack was stopped. An earlier
  concurrent attempt failed from Docker memory pressure, not an IaC validation error.
- Complete local harness passed: **16 new simulator observations** persisted by the
  real archiver through Kafka SASL_SSL into PostgreSQL with `verify-full`; the live
  query returned `OK` with state newer than the start of that run. This additionally
  exercises processor Kafka Streams, RabbitMQ TLS/OAuth2, dispatcher and query replies.
- All ten security tests passed in that final run (0 failures/errors/skips), and the
  callback regression test passed separately. Test containers stopped afterwards.
- Docker Desktop exposes about 3.5 GiB RAM. The previous development stack was
  temporarily paused to avoid running two full stacks concurrently. Kafka permits
  two CPUs; fresh installations use three partitions for internal offset/transaction
  topics. Existing topic partition counts are not changed. Negative security probes
  allow 30 seconds for connection establishment but still require the specific
  authentication/authorization failure; a generic timeout fails the test.
- Regenerated the authenticated Terraform plan after the firewall update:
  **53 additions, 0 changes, 0 deletions**, not applied. Restored the seven original
  development containers after completing the isolated test.

No AWS resources were created or started during these changes.
