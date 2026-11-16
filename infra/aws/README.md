# AWS laboratory foundation

**Entrega actual:** [RELEASE-READINESS.md](RELEASE-READINESS.md), con pruebas locales
reales y preparación hasta el 22/11/2026. [DEPLOYMENT.md](DEPLOYMENT.md),
[POWER-GUIDE.md](POWER-GUIDE.md), [TEARDOWN.md](TEARDOWN.md) y [COSTS.md](COSTS.md)
contienen despliegue, apagado/encendido, eliminación y revisión económica.
No se ha desplegado AWS. El uso propuesto de créditos requiere revisar cobertura
y aprobar el plan; el estado PAID/ACTIVE se conserva.

**Revisión autenticada actual:** [FEASIBILITY.md](FEASIBILITY.md). AWS informa
plan **PAID/ACTIVE**, USD 100 y una organización existente. No se modificaron esos
estados. Solo lectura y preparación: sin apply ni autorización para consumir créditos.

**Restricción actual: solo cuenta gratuita.** Ver [FREE-PLAN.md](FREE-PLAN.md)
para cobertura, consumo de créditos y condiciones antes de desplegar. Ninguna
tarifa o saldo histórico en este archivo demuestra cobertura vigente.

**Current workstation review:** see [READINESS.md](READINESS.md) and
[ACCESS.md](ACCESS.md). The historical AWS session/credit assertions below
are superseded by FEASIBILITY.md. No account-plan change, credit
use, Organizations, SSO, apply or launch is authorized.

Account `223455088753`, region `us-east-2`, profile `smarttraffic-project`.
This foundation is based on `origin/develop` at `84d71a7` (PR #19 merged).
It provisions infrastructure only. A successful plan or bootstrap does **not**
mean that SmartTraffic or its secure message flow has been deployed.

## Topology and access

| Zone | Private IP | Type | RAM | Root gp3 |
|---|---|---|---|---|
| auth | 10.42.10.10 | t3.small | 2 GiB | 16 GiB |
| data | 10.42.10.11 | m7i-flex.large | 8 GiB | 40 GiB |
| ingest | 10.42.10.12 | t3.small | 2 GiB | 16 GiB |
| core | 10.42.10.13 | c7i-flex.large | 4 GiB | 24 GiB |
| app | 10.42.10.14 | t3.small | 2 GiB | 24 GiB |

One dedicated VPC and a security group per zone. All nodes are in one AZ for this lab.
Private IPv4 addresses are stable. Public IPv4 addresses provide outbound access
for SSM and package/image downloads without a NAT gateway. No public ingress,
SSH, broker management ports or database access is allowed. Initially access the
dashboard through an SSM port-forwarding session; public HTTPS exposure is a
separate deployment step requiring a hostname and certificate.

Service ports: Auth HTTPS 8443, Kafka SASL_SSL 9093, AMQPS 5671, PostgreSQL 5432.
The data zone can fetch JWKS from Auth. Allowing that broker-to-authority flow is
an explicit addition to the workshop's basic network matrix.

Host nftables has INPUT, OUTPUT and FORWARD default DROP. Application containers
must use host networking: Docker bridge forwarding is intentionally denied.
Outgoing HTTPS is a documented maintenance exception, not a claim of destination-
restricted production egress. DNS, DHCP, EC2 metadata and AWS time synchronization
have explicit rules. Packages install before the host firewall activates; SG rules
already restrict ingress. nftables rules are syntax-checked before activation.

EBS is encrypted. IMDSv2 is required. Hop limit 1 alone does not isolate containers
using host networking: nftables permits metadata access only to root for SSM.
Application processes must run as non-root. Host/container root remains a trusted
administrative boundary. SSM uses a separate role
per zone. No secrets are placed in user data or Terraform. Application-specific
secret permissions and delivery remain to be added after broker validation.

## Reproduce the plan

User decision on 2026-11-16: **prepare and validate only; do not launch VMs**.
No apply is authorized by this document. The saved plan is for review.

From PowerShell with AWS CLI and Terraform available on PATH (or supply
`-TerraformPath /path/to/terraform`). `-Runtime Docker` retains the container option:

```powershell
./infra/aws/Invoke-Terraform.ps1 -Action init
./infra/aws/Invoke-Terraform.ps1 -Action fmt
./infra/aws/Invoke-Terraform.ps1 -Action validate
./infra/aws/Invoke-Terraform.ps1 -Action plan
# Review-only plan including the five instances; still does not apply:
./infra/aws/Invoke-Terraform.ps1 -Action plan -CreateInstances
./infra/aws/Invoke-Terraform.ps1 -Action show
```

The helper exports temporary AWS credentials into the child process environment
and restores the previous values afterwards. It has no apply/destroy action.
Both the helper and raw Terraform leave instances disabled by default.
`-CreateInstances` includes the five VMs in a review plan only. A plan without
instances still contains VPC/IAM resources and does not authorize apply.
Review `lab.tfplan` before any apply. The plan and local state
are ignored by Git; protect and retain state if resources are created. This is a
single-operator lab backend, not a configured shared state service.

Root disks survive termination to protect PostgreSQL/Kafka volumes. They continue
to incur storage cost until explicitly removed after backup/recovery review.
Stopping instances stops compute charges, but not EBS charges. No Elastic IPs,
NAT gateways or paid VPC interface endpoints are declared.

## Gates before application rollout

- Local Kafka OAUTHBEARER and RabbitMQ OAuth2 validators are configured and tested.
  Repeat those checks against the deployed brokers and deployment certificates.
- Per-service RabbitMQ scopes and dashboard JWT registration are implemented and
  locally tested; supply deployment secrets and verify them with the cloud broker.
- Supply a CA, matching RS256 keys, server certificates with correct SANs, and JVM
  trust stores for HTTPS and broker TLS. Test rejection of wrong CA/hostname/token.
- Per-zone Compose, resource limits and persistent volumes are prepared in
  [zones](zones/README.md). Image/certificate delivery and Compose plugin installation
  on actual VMs remain deployment steps. The bootstrap now installs checksum-pinned
  Compose 5.1.3 and arms a systemd poweroff timer before package downloads.
- Exclusive reply queues and observed-state queries are implemented and tested.
  Road-graph routing/travel-time calculation still needs domain data and logic.
- Run complete ingest -> Kafka -> processor/archiver -> RabbitMQ -> dashboard,
  alert and query tests. Unit/build checks are not substitutes for these tests.

See [VALIDATION.md](VALIDATION.md) and the local integration harness for the exact
executed checks. Local results do not prove VM bootstrap or cloud network behavior.

## Historical price snapshot (2026-11-16, preceding workstation)

Queried the AWS Price List API for Ohio, Linux, shared tenancy, On-Demand:

- t3.small: USD 0.0208/hour (three nodes).
- m7i-flex.large: USD 0.09576/hour (one node).
- c7i-flex.large: USD 0.08479/hour (one node).
- Public IPv4: USD 0.005/address/hour (five addresses).
- gp3: USD 0.08/GiB-month (120 GiB total, baseline IOPS/throughput).

Calculated using PowerShell: USD 0.26795/hour for running compute plus IPv4,
USD 9.60/month for disks; USD 205.2035 for a 730-hour month. An eight-hour
session plus one day of retained disks is approximately USD 2.46. Excludes
traffic, snapshots, logs, taxes and future application-related services.
Credits are consumed by eligible usage; Free Tier eligibility is not unlimited
free operation. The account API reported USD 100 remaining and a 32-vCPU quota.
No running EC2 instances or existing budgets were returned by the inventory.

The prepared stop timer uses `lab_duration_minutes` (default 60, range 1–480)
from each boot, with EC2 instance-initiated shutdown explicitly set to stop.
Neither the timer nor budget notifications have been deployed or tested on EC2.
EBS remains billable after stop; bootstrap/OS failure can prevent this timer from
protecting the account. Verify the timer and notifications during a separately
authorized launch window. A budget alert is not a spending cap.
