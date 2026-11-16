# Despliegue limitado a la cuenta gratuita

**Alcance histórico:** la instrucción posterior permite preparar el uso de créditos
promocionales sobre la cuenta PAID existente, sin cargos adicionales intencionales.
La entrega vigente está en [RELEASE-READINESS.md](RELEASE-READINESS.md). Todavía
no hay autorización para apply ni cambios del plan/Organizations/SCP.

**Resultado posterior de la verificación:** la sesión autenticada devuelve
**PAID/ACTIVE**, USD 100 y Organizations existente. Ver [FEASIBILITY.md](FEASIBILITY.md).
El requisito de Free Plan no se cumple en el estado observado. La sección de
evidencia de esta preparación que sigue es histórica; no se modificó la cuenta.

Restricción solicitada el 2026-11-16: usar solamente recursos cubiertos por
la cuenta gratuita, sin habilitar Paid Plan. Este documento prepara la decisión;
no acredita cobertura de la cuenta ni autoriza arrancar recursos.

## Qué significa gratuito para este laboratorio

AWS distingue Free Plan y Paid Plan. En el Free Plan actual el uso elegible consume
créditos; AWS indica que no genera cargos mientras se conserva ese plan. Termina
a los seis meses o al agotarse los créditos, lo que ocurra primero, y la cuenta
se cierra automáticamente. Guardar evidencia y datos fuera de AWS antes de ese
momento. No actualizar el plan ni unirse a Organizations: AWS documenta que esto
último provoca actualización automática a Paid Plan.

La etiqueta EC2 «Free tier eligible» no equivale a uso ilimitado o sin consumo
de créditos. Para cuentas creadas desde el 15 de julio de 2025, AWS incluye
`t3.small`, `m7i-flex.large` y `c7i-flex.large` entre los tipos elegibles.
La cobertura real requiere verificar fecha de creación, plan, vigencia, saldo,
AMI, almacenamiento, IPv4, transferencia y uso del resto de la cuenta.

Si el requisito también excluye consumir créditos, este despliegue de cinco
EC2 no tiene cobertura gratuita demostrada: continuar únicamente con preparación
y pruebas locales, sin lanzar AWS.

## Evaluación de la configuración preparada

| Recurso | Configuración | Condición antes de crear |
|---|---|---|
| EC2 | 3 t3.small, 1 m7i-flex.large, 1 c7i-flex.large | Tipos/AMI elegibles, cuota y capacidad, créditos vigentes |
| EBS | 120 GiB gp3 cifrado | Cobertura del uso y retención en el saldo disponible |
| IPv4 | 5 direcciones públicas para salida HTTPS/SSM | Incluir su tarifa base en el consumo estimado |
| Red | VPC, subred y una AZ; sin NAT Gateway ni endpoints de interfaz | Mantener flujos privados entre servicios y entrada pública cerrada |
| Servicios | Kafka, RabbitMQ y PostgreSQL dentro de VM data | Sin sustituir por MSK, Amazon MQ o RDS |
| Imágenes | Construcción local y bundles por zona | Verificar cobertura del canal de transferencia antes de contratarlo |

No convertir las cinco máquinas a micro por la etiqueta gratuita. La VM data
tiene límites de 2 GiB para Kafka y 768 MiB para cada uno de RabbitMQ y PostgreSQL,
además del sistema operativo. Esa configuración no demuestra funcionamiento en
una micro de 1 GiB. Se mantienen las cinco zonas y TLS/autenticación por servicio.

## Comprobaciones reproducibles sin crear recursos

Desde este worktree, con perfil individual autenticado según [ACCESS.md](ACCESS.md):

```powershell
aws sts get-caller-identity --profile smarttraffic-project --region us-east-2
./infra/aws/Get-Readiness.ps1 -Profile smarttraffic-project
aws ec2 describe-instance-types --profile smarttraffic-project --region us-east-2 --filters Name=free-tier-eligible,Values=true --query 'InstanceTypes[].InstanceType'
```

Confirmar en el resultado `get-account-plan-state` que la cuenta es
`223455088753`, el plan es gratuito y sigue vigente; contrastar saldo y vencimiento
en Billing. Si falta acceso, saldo, vigencia o cobertura, no aplicar Terraform.
El inventario regional no descarta consumo en otras regiones: revisar también
Billing y EC2 Global View. La política de lectura está preparada, no validada por IAM.

Preparar un plan nuevo con las cinco VMs solo para revisión. Calcular una ventana
concreta y todo el almacenamiento retenido, dejando reserva para otros consumos.
Las tarifas históricas de README no son cotización vigente ni prueba de cobertura.
El apagado preparado es 60 minutos desde cada arranque, pendiente de validación
en EC2; no es un límite de gasto. Detener EC2 conserva EBS; terminar las máquinas
también conserva sus discos por configuración. No borrar datos automáticamente.

Antes de `apply`, el propietario debe confirmar la ventana y el presupuesto
concretos, como exige el encargo adjunto. La instrucción «solo recursos de la
cuenta gratuita» no especifica esa ventana. Preparar alerta de costo cero sin
acciones de pago y comprobarla antes del lanzamiento.

## Evidencia de esta revisión

- `git fetch origin --prune` completado; `origin/develop` sigue en `72e69cb`.
- `3291eeb` y `a205942` están integrados según su ascendencia en Git.
- AWS CLI devuelve perfil `smarttraffic-project` inexistente; STS no autenticado.
- No se puede confirmar todavía saldo, vigencia, recursos existentes ni cobertura.
- No se ejecutó `apply`, no se arrancaron VMs ni se validó el flujo en AWS.

## Fuentes oficiales consultadas el 2026-11-16

- [AWS: planes gratuito y de pago](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/free-tier-plans.html).
- [AWS: tipos EC2 elegibles y diferencias por fecha de cuenta](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html).
- [AWS: precios de IPv4 y red](https://aws.amazon.com/vpc/pricing/).
