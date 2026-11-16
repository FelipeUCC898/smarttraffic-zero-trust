# Preparation validation — 2026-11-16

User scope: prepare and validate only. No resources were applied or started.

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
SSM registration, cloud certificate delivery, Kafka TLS/OAuth2 connections and
end-to-end traffic have not been tested. Terraform planning cannot establish these.
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

The local broker uses static public-key pinning. Cloud JWKS discovery/rotation,
Kafka ACLs and end-to-end application deployment have not been exercised.

No AWS resources were created or started during these changes.
