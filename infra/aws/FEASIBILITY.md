# Viabilidad AWS con presupuesto adicional USD 0

Revisión: 2026-11-16, America/Bogota. Consultas AWS de lectura y pruebas locales;
sin cambios AWS, `apply`, arranque EC2 ni altas de servicios. El propietario está
dispuesto a usar sus créditos para el proyecto, pero exige autorización explícita
antes de desplegar. [RELEASE-READINESS.md](RELEASE-READINESS.md) contiene la entrega
actual, pruebas reales y artefactos preparados para finalizar el domingo 22.

## Dictamen

**Laboratorio local validado; despliegue AWS preparado y pendiente de aprobación.**
El consumo base de la semana completa sería USD 47.23, dentro de USD 100 solo
si los créditos siguen vigentes, aplican a estos conceptos y no los consume otra
carga. Ver [COSTS.md](COSTS.md): escenarios de sesiones y cobertura pendiente.
La cuenta es **PAID/ACTIVE** y pertenece a una organización; no se modificó ninguno
de esos estados. La instrucción posterior permite preparar el uso de créditos,
pero no garantiza presupuesto adicional USD 0 ni autoriza todavía apply.

La API confirma el estado actual, no quién o cuándo cambió el plan ni por qué se
creó la organización. AWS documenta que unirse a Organizations puede actualizar
automáticamente el plan; no atribuir esa causa a esta cuenta sin evidencia histórica.
No se intenta abandonar la organización ni cambiar el plan.

## Cuenta, saldo y permisos

| Consulta | Resultado actual |
|---|---|
| STS | Cuenta 223455088753, assumed-role/AccountFullAccessRole, credenciales temporales; no root |
| Región configurada y consultada | us-east-2 |
| Plan | accountPlanType=PAID, accountPlanStatus=ACTIVE |
| Saldo informado | accountPlanRemainingCredits: 100.00 USD |
| Vencimiento | La respuesta no incluye accountPlanExpirationDate; no verificable con esa respuesta |
| Uso gratuito | get-free-tier-usage devuelve lista vacía; no demuestra cobertura ni facturación cero |
| Organizations | Existe o-ba0y79umu2, FeatureSet=ALL; cuenta administradora 342718283791 |
| Presupuestos | describe-budgets no devolvió presupuestos; no se creó ni comprobó entrega de alertas |

La consulta de Organizations omite el correo de la cuenta administradora de la
evidencia persistida. No se exportaron credenciales, claves ni tokens.
No se utilizó Cost Explorer ni se activó ningún servicio de facturación.
Comprobar vencimiento, condiciones de créditos y posible uso compartido de
créditos en Billing con el administrador autorizado de facturación. No se afirma
saldo dedicado exclusivamente a SmartTraffic ni costo histórico cero.

## Recursos y cuotas

En Ohio las consultas no devolvieron EC2, EBS, direcciones elásticas, snapshots
propios, NAT Gateways, endpoints VPC, RDS, ELBv2, Amazon MQ ni MSK. Existe una VPC
predeterminada. Esto cubre los servicios consultados, no todos los recursos AWS.

Se consultaron EC2/EBS/direcciones/snapshots en las 17 regiones habilitadas:
Ohio permitió las cuatro consultas y devolvió listas vacías. Las otras 16 regiones
rechazaron las 64 consultas por **denegación explícita de una SCP de Organizations**.
No se ampliaron privilegios ni se intentó eludirla. No es posible asegurar ausencia
de recursos o cargos en esas regiones. Si se necesita completar el inventario,
el administrador puede aportar un inventario desde una identidad ya autorizada;
este trabajo no requiere conceder escritura ni retirar la SCP.

Cuota regional Standard On-Demand: **32 vCPU**; demanda prevista: **10 vCPU**.
Los tres tipos previstos figuran como elegibles y ofrecidos en us-east-2a.
Oferta no garantiza capacidad de lanzamiento, y plan no garantiza autorización
efectiva de RunInstances/PassRole. No se ensayaron acciones de escritura, tampoco
con dry-run. AMI Amazon Linux 2023 x86_64 consultada:
`ami-0df8a753693e6366d`, FreeTierEligible=true. La AMI se resuelve dinámicamente;
un plan futuro puede seleccionar otra versión.

## Costos consultados

Price List API, Ohio, Linux, shared tenancy, On-Demand; sin descuentos ni créditos:

| Concepto | Cantidad | Tarifa unitaria USD | Subtotal |
|---|---:|---:|---:|
| t3.small | 3 | 0.02080/h | 0.06240/h |
| m7i-flex.large | 1 | 0.09576/h | 0.09576/h |
| c7i-flex.large | 1 | 0.08479/h | 0.08479/h |
| IPv4 en uso | 5 | 0.00500/h | 0.02500/h |
| EBS gp3 básico | 120 GiB | 0.08/GiB-mes | 9.60/mes |

Total con instancias encendidas: **USD 0.26795/h más EBS**. Aproximaciones usando
730 horas/mes para prorratear discos:

| Escenario | Consumo base estimado USD |
|---|---:|
| 1 hora encendido y 24 horas de discos | 0.58 |
| 8 horas encendido y 24 horas de discos | 2.46 |
| 730 horas encendido y discos todo el mes | 205.20 |
| Solo discos retenidos todo el mes, EC2 detenidas | 9.60 |

Fórmula: `0.26795 * horas_encendidas + 9.60 * horas_discos / 730`.
Excluye transferencia, snapshots, logs, operaciones/servicios adicionales,
impuestos y consumo de otras cargas. Los USD 100 no sostienen un mes continuo
de esta configuración aun si cubrieran todos esos conceptos. No se ha verificado
la aplicación de los créditos a una factura concreta. Las tarifas base no son
la factura final ni una garantía de gasto máximo.

## Servicios y beneficios

- EC2/AMI y gp3 figuran como elegibles; su etiqueta no demuestra una asignación
  gratuita ilimitada para esta cuenta Paid. EC2, EBS e IPv4 tienen tarifa base.
- Session Manager y Run Command en EC2 no tienen cargo adicional según su página
  de precios; los recursos EC2/EBS/red y servicios opcionales siguen teniendo costo.
- Se conserva la red básica VPC/subred/SG/IAM y una sola AZ. No se añaden NAT,
  endpoints de interfaz, balanceadores, MSK, Amazon MQ, RDS, ECR ni S3.
- Kafka, RabbitMQ y PostgreSQL continúan en la VM data. No se compromete su
  funcionamiento reduciéndola a una micro de 1 GiB.
- No se declara «servicio gratuito verificado para la cuenta» a partir de una
  lista de Free Tier vacía. Límites disponibles y cobertura requieren evidencia
  de Billing. Tampoco se habilitan acciones de presupuesto ni logging de pago.

## Terraform y pruebas

Se generó un plan autenticado nuevo desde esta estación: **53 creaciones,
0 cambios y 0 eliminaciones**, incluyendo cinco EC2. Terraform 1.13.5;
proveedor AWS 6.73.0. `lab.tfplan` permanece local e ignorado; no se aplicó.
Resumen compartible en `evidence/free-plan-review/plan-summary.json`.
No adjuntar el plan binario, estado ni JSON completo a Git.

Verificaciones de preparación y ejecución local:

- Terraform fmt y validate correctos; 5 pruebas con proveedor simulado correctas,
  incluido rechazo del cutoff vencido. Plan 53/0/0 actualizado con corte del domingo.
- Test-Preparation: sintaxis, planes de revisión, identidad/restauración de entorno
  y bundles de imágenes con mocks correctos; no confundir mocks con despliegue.
- Cinco Compose por VM validados con sus variables necesarias.
- Prueba HTTPS real AuthTokenClient local: 1 ejecutada, sin fallos; verifica
  confianza en CA y rechazo sin ella. No usa certificados de despliegue.
- Docker Desktop se inició: motor Linux disponible. El flujo completo pasó con
  18 observaciones nuevas, consulta reciente, WebSocket/alerta y renovación corta.
- Pasaron 10 pruebas reales Kafka/PG, 8 RabbitMQ, 2 JPA/PG, 2 de roles SQL,
  HTTPS, 4 del receptor de entrega y la recuperación (481 conservadas → 488 filas).
  Ver RELEASE-READINESS.md para distinguir intentos fallidos y repeticiones.

Las ocho suites Maven se repitieron en modo offline con Java 17 y repositorio
local: **413 casos declarados, 402 ejecutados, 11 omitidos, 0 fallos y 0 errores**.
La prueba HTTPS omitida en esa ejecución general pasó por separado antes de ella:
**403 ejecuciones correctas en total**; quedan 10 casos RabbitMQ/PostgreSQL sin
ejecutar en esa ejecución anterior; ahora se completaron por separado. Desglose
anterior en `evidence/free-plan-review/java-summary.json` y logs por
módulo. No se encontraron fallos funcionales que corregir en las suites ejecutadas.
Los mensajes de configuración inválida del simulador corresponden a pruebas
negativas que terminaron correctamente. Persisten advertencias Maven sobre versión
de plugin/encoding, sin fallos de compilación. La preparación posterior añade V2
y roles SQL; no cambia la lógica de dominio Java.

Bootstrap, timer de 60 minutos, cutoff absoluto 23/11 05:00 UTC, SSM, nftables
y aplicaciones no están desplegados en AWS. Session Manager plugin 1.2.896.0
instalado localmente con autorización y firma verificada.
Detener instancias conserva discos; terminar también los conserva por la
configuración preparada. No se borra ningún dato como medida de ahorro automática.
El timer y alertas no son límites de gasto. Las demás puertas de certificados,
permisos PostgreSQL, imágenes, rotación y pruebas E2E permanecen en READINESS.md.

## Evidencia y reproducción

- `evidence/free-plan-review/aws-read-only.json`: cuenta, plan, saldo, organización,
  recursos regionales, cuota, tipos, AMI y precios EC2.
- `ebs-price.json`, `ipv4-price.json`: respuestas públicas de Price List.
- `regional-compute-storage.json`: resultados y rechazos por región.
- `plan-summary.json`: acciones y cinco máquinas del plan local.
- `cost-summary.json`: cálculo de los escenarios de costo base.
- `java-summary.json`: totales Maven por módulo, sin contar el HTTPS separado.
- `*-tests.log`: ejecución Java anterior; logs actuales bajo `.tools` y resumen
  en `evidence/release-validation-2026-11-16.json`.

Desde el worktree `.worktrees/aws-readiness`, con la misma identidad temporal:

```powershell
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Inspect-FreePlan.ps1
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Inspect-Regions.ps1
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Invoke-Terraform.ps1 -Action plan -CreateInstances -TerraformPath .tools/terraform/terraform.exe
```

Los dos inspectores contienen solo consultas de lectura y rechazan otra cuenta o
root. Pueden registrar errores y continuar; inspeccionar status/error antes de
concluir que no existen recursos. En el entorno restringido el perfil no es visible;
la verificación real necesitó acceso aprobado fuera del sandbox, sin cambiar perfiles.

## Fuentes oficiales

- [Planes AWS y relación con Organizations](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-plans.html).
- [Elegibilidad EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html).
- [Precios IPv4](https://aws.amazon.com/vpc/pricing/).
- [Precios EBS](https://aws.amazon.com/ebs/pricing/).
- [SSM: precios por función](https://aws.amazon.com/systems-manager/pricing/).
