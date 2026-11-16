# Five-VM runtime (prepared, not deployed)

Current deployment uses the reviewed `data-roles.yml` and `core-roles.yml`
overlays as described in [DEPLOYMENT.md](../DEPLOYMENT.md). The explicit SQL
provisioning step separates bootstrap (data only), migration and runtime roles;
runtime DML and DDL rejection passed locally. Never send the bootstrap password
to core. Local end-to-end evidence is in [RELEASE-READINESS.md](../RELEASE-READINESS.md).

These independent Compose files use host networking, matching Terraform's
default-deny forwarding rules. JSON syntax is valid YAML. `infra/runtime/services.yml`
is the common service catalog used by the local integration test; `Test-Zones.ps1`
checks that each VM definition matches it, apart from host networking, no builds
on the VM, and loopback-only web interfaces.

| VM | Services | Memory limits total |
|---|---|---|
| auth | Auth Server | 512 MiB |
| data | Kafka (single-node KRaft), RabbitMQ, PostgreSQL | 3584 MiB |
| ingest | Simulator | 640 MiB |
| core | Processor, archiver | 1408 MiB |
| app | Dispatcher, query, dashboard | 1280 MiB |

Kafka external clients use 9093 SASL_SSL/OAUTHBEARER. Its administration/interbroker
9092 and KRaft controller 9094 bind **only to loopback**, use mutual TLS, and have
no security-group ingress rule. The broker verifies RS256 against Auth's HTTPS
JWKS endpoint, expected issuer and audience. Only the broker certificate principal
is a superuser. ACLs restrict each application to its topics/groups; internal
Kafka Streams state and transaction IDs share the `density-processor-demo` prefix.
`kafka-init.sh` is repeatable and adds ACLs; it does not revoke previously granted
permissions. Review existing ACLs when migrating an existing broker.

The workshop demo uses artificial road length/traffic values explicitly marked
in the processor-demo profile and simulator fixture. It does not implement road
graph routing. RabbitMQ pins the JWT public key; key rotation requires coordinated
configuration and broker restart. This is not an HA deployment.

## Before a future authorized launch

1. The prepared EC2 bootstrap installs Compose 5.1.3 with a pinned SHA-256 before
   Docker starts. Verify its version; no Compose installation or VM
   execution is claimed by local validation.
2. Build images locally with the integration harness. Transfer the reviewed images
   using [Export-Images.ps1](../Export-Images.ps1) and [Import-Images.sh](../Import-Images.sh)
   through an approved delivery channel. These record and verify per-zone image IDs
   and archive SHA-256; no registry or cloud storage is provisioned. The `smarttraffic/*:aws-lab` names are
   local tags, not published Docker Hub images. Start with `--no-build --pull never`.
3. Place the repository's `infra/runtime` and `infra/aws/zones` under PROJECT_ROOT.
   Create each VM's environment file from `runtime.env.example`, including **only**
   variable names used by its Compose file. Never deliver other services' secrets
   to ingest/core/app. Keep files outside Git and chmod 600 on Linux.
4. Provision a deployment CA and separate certificates with IP SANs: Auth
   10.42.10.10; Kafka, RabbitMQ and PostgreSQL 10.42.10.11. Kafka also needs SAN
   127.0.0.1 for its private listeners and CN=kafka for the pinned admin principal.
   Kafka certificate EKU must include serverAuth and clientAuth. Provision
   `ca.pem`, `truststore.p12`, and public JWT `jwt-public.pem` as required by mounts.
   Deliver `jwt-private.pem` and `auth-server.p12` **only to auth**; `kafka-combined.pem`
   (PKCS8 key + certificate), `rabbitmq.pem/key`, `postgres.pem/key` only to data.
   Never deliver the CA private key to any VM. Use one truststore password matching
   the Auth PKCS12 and each JVM truststore, or adapt the explicit variables.
5. Ensure mounted secrets are readable only by root and the relevant container
   UID/group (inspect each image's USER). Do not reproduce the local harness's
   permissive fixture modes. The secrets directory is root-only; do not mount it
   wholesale. PostgreSQL copies its key to mode 600 before switching to postgres.
6. Start auth, validate HTTPS JWKS with the deployment CA, then data; wait for its
   health checks. Run `docker compose --env-file /secure/data.env -f data.yml exec
   -T kafka bash /run/config/kafka-init.sh`. Start app subscribers, core, then ingest.
   All start commands require the separately approved AWS launch window.
7. Check fresh PostgreSQL rows and a current-state query, then dashboard and alert
   delivery, SSM access, host firewall drops, token renewal and restart recovery
   in the real VMs. These checks cannot be replaced by local Docker results.

Persist named volumes. Do not use `down -v` on a deployment. PostgreSQL enables
TLS and rejects TCP plaintext; the archiver uses `sslmode=verify-full`. Database
credentials in the base definition still use the image bootstrap superuser:
apply the roles overlays and explicit provisioning for deployment. Core retains
migrator credentials for Flyway startup; independent migration credential removal
is still a production hardening task. No public UI port is opened; use SSM forwarding to 127.0.0.1:8081
(dashboard) or 127.0.0.1:8092 (query).

Validate the definitions without starting anything:

```powershell
./infra/aws/Test-Zones.ps1 -FixtureEnv ./.tools/secure-e2e/runtime.env
```
