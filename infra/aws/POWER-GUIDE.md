# Apagar y encender SmartTraffic: guía paso a paso

Las cinco VMs **todavía no están creadas**. Este tutorial se utiliza después del
despliegue autorizado. Ejecutar detener/arrancar o comandos SSM modifica AWS:
requiere autorización, distinta de las consultas de lectura.

## Entrar y encontrar las máquinas

1. Abrir https://console.aws.amazon.com/ e iniciar sesión por el acceso temporal
   ya proporcionado. Elegir cuenta **223455088753** y el rol autorizado. No usar root.
2. Comprobar cuenta/rol en la esquina superior derecha. No entrar a otra cuenta
   de la organización ni cambiar sus políticas.
3. En el selector de región superior elegir **US East (Ohio), us-east-2**.
   No confundir con N. Virginia/us-east-1.
4. Buscar **EC2** y abrir **Instances → Instances**. Quitar el filtro de solo
   instancias en ejecución para poder ver también las detenidas.
5. Filtrar `tag:Project = smarttraffic` y `tag:Environment = lab`. Deben aparecer
   los cinco nombres siguientes. Si hay más/menos, revisar los IDs del output
   Terraform y no seleccionar otras cargas.

| Nombre | Tag Zone | Función |
|---|---|---|
| vm-auth | auth | Tokens HTTPS y claves públicas JWKS |
| vm-data | data | Kafka, RabbitMQ y PostgreSQL |
| vm-ingest | ingest | Simulador que genera observaciones |
| vm-core | core | Procesador y archivador |
| vm-app | app | Consultas, dispatcher y dashboard |

## Detener de forma ordenada

Para no seguir enviando datos mientras se apagan los consumidores, primero
detener aplicaciones en este orden: **ingest → core → app → data → auth**.
No eliminar contenedores, volúmenes ni archivos.

En consola: seleccionar una instancia → **Connect → Session Manager → Connect**.
La conexión necesita SSM Online; no abrir SSH ni crear una clave EC2 para resolver
un problema de SSM. En la terminal, convertirse en root con `sudo -i`, revisar
hostname y ejecutar el Compose de su zona. Ejemplo ingest:

```bash
hostname
cd /opt/smarttraffic/repo
docker compose --env-file /opt/smarttraffic/runtime.env -f infra/aws/zones/ingest.yml stop -t 60
```

En core añadir `-f infra/aws/zones/core-roles.yml`, y en data
`-f infra/aws/zones/data-roles.yml`, antes de `stop -t 60`. En app/auth usar su
archivo base. Esperar que la operación termine antes de la siguiente zona.

Después volver a EC2, seleccionar **solo las cinco instancias verificadas**, y
elegir **Instance state → Stop instance → Stop**. La consola presenta una
confirmación: revisar nombres/IDs antes de aceptar. No elegir Terminate ni Force stop.
No usar hibernación para este laboratorio.

La secuencia visible es `Running → Stopping → Stopped`. Actualizar la tabla
hasta que **las cinco estén Stopped**. `Stopping` todavía no es confirmación de
apagado completo. Anotar hora e IDs. Si una no se detiene, revisar su estado;
no marcar completado el cierre ni forzar apagado sin evaluar los datos.

Si hace falta cortar consumo con urgencia y SSM no funciona, Stop de EC2 evita
esperar a la aplicación, pero puede interrumpir mensajes/transacciones en curso.
Luego comprobar la recuperación y la persistencia. Nunca sustituir Stop por Terminate.

## Lo que sigue teniendo costo

- EBS de las cinco máquinas: **120 GiB**, aproximadamente USD 9.60/mes en total,
  hasta borrarlos. El estado Stopped no libera esos discos.
- Las IPv4 **automáticas de esta configuración se liberan al detener la instancia**;
  al arrancar cambian. Las IP privadas permanecen. Una **Elastic IP**, si alguien
  la creó aparte, continúa cobrándose incluso sin asociar: revisar Elastic IPs.
- Snapshots, AMIs con snapshots, buckets, logs, NAT Gateways, endpoints de interfaz,
  balanceadores, bases/brokers administrados u otras cargas continúan según uso.
  No se han agregado esos servicios al plan SmartTraffic, pero deben inventariarse.
- Detener no revierte cargos ya generados ni garantiza que un crédito los cubra.

Usar la lista de [COSTS.md](COSTS.md) después de cada parada.

## Volver a encender

**Después del corte del 22 de noviembre no reiniciar sin revisar la ventana.**
El guard preparado apagará nuevamente una VM arrancada después del límite;
no deshabilitarlo como arreglo improvisado.

Antes del corte, comprobar créditos/vencimiento y que la nueva sesión cabe en
el presupuesto. En EC2 seleccionar y encender por etapas:

1. vm-auth → **Instance state → Start instance**. Esperar Running, status checks
   correctos y SSM Online. Arrancar su Compose con `up -d --no-build --pull never`.
   Verificar HTTPS/JWKS.
2. vm-data: esperar SSM y arrancar su Compose con overlay data-roles. Verificar
   salud de brokers/BD; ejecutar kafka-init solo después de Kafka healthy.
   No volver a generar passwords ni ejecutar provisión de roles sin revisar.
3. vm-app: arrancar consumidores y comprobar query/dashboard por túnel.
4. vm-core: arrancar Compose con core-roles; comprobar conexiones y estado procesado.
5. vm-ingest al final: comprobar nuevas filas, consulta reciente y dashboard.

`Running` indica que el sistema operativo está encendido; no que SmartTraffic
funcione. Si las aplicaciones se detuvieron mediante Compose, `restart: unless-stopped`
puede mantenerlas apagadas tras encender el host: ejecutar `up` en cada zona.
Mantener los mismos volúmenes, CA, credenciales y `PROCESSOR_APPLICATION_ID`.
La configuración usa las IP **privadas** estables, por lo que cambiar la pública
no requiere desactivar validación de certificados.

El timer por arranque está preparado para **60 minutos** y el corte absoluto
para el domingo. Encender no cambia el presupuesto ni amplía el corte. Revisar
ambos con `systemctl list-timers 'smarttraffic-lab-*'`.

## AWS CLI: consultar, detener y arrancar

Usar PowerShell 7 con el perfil temporal autenticado. Puede iniciarse con
`.tools/pwsh/pwsh.exe` desde el worktree. El plugin Session Manager ya está instalado;
si esta terminal conserva el PATH anterior a la instalación, añadirlo solo al proceso:

```powershell
$env:PATH = 'C:\Program Files\Amazon\SessionManagerPlugin\bin;' + $env:PATH
session-manager-plugin --version
```

Las consultas siguientes son
de lectura. Un resultado vacío antes del despliegue es normal:

```powershell
aws sts get-caller-identity --profile smarttraffic-project --region us-east-2
$raw = aws ec2 describe-instances --profile smarttraffic-project --region us-east-2 --filters Name=tag:Project,Values=smarttraffic Name=tag:Environment,Values=lab --query 'Reservations[].Instances[].{Id:InstanceId,Zone:Tags[?Key==`Zone`].Value|[0],Name:Tags[?Key==`Name`].Value|[0],State:State.Name}' --output json --no-cli-pager
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar EC2' }
$vms = @($raw | ConvertFrom-Json | Where-Object State -ne 'terminated')
$vms | Format-Table
if ($vms.Count -ne 5 -or @($vms.Zone | Sort-Object -Unique).Count -ne 5) { throw 'Revisar inventario: se esperan cinco zonas distintas' }
$ids = @($vms.Id)
```

Comparar esos IDs con Terraform antes de continuar. **Después de autorizar la
parada y detener las aplicaciones** según lo explicado arriba:

```powershell
aws ec2 stop-instances --profile smarttraffic-project --region us-east-2 --instance-ids $ids
if ($LASTEXITCODE -ne 0) { throw 'La parada falló; revisar cada instancia' }
aws ec2 wait instance-stopped --profile smarttraffic-project --region us-east-2 --instance-ids $ids
if ($LASTEXITCODE -ne 0) { throw 'No se confirmó Stopped en todas las instancias' }
aws ec2 describe-instances --profile smarttraffic-project --region us-east-2 --instance-ids $ids --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name}' --output table
```

Para un arranque autorizado, ejecutar por zona en el orden anterior y comprobar
SSM/aplicación entre etapas. Ejemplo de **una** etapa, auth:

```powershell
$authId = ($vms | Where-Object Zone -eq 'auth').Id
aws ec2 start-instances --profile smarttraffic-project --region us-east-2 --instance-ids $authId
aws ec2 wait instance-running --profile smarttraffic-project --region us-east-2 --instance-ids $authId
aws ssm describe-instance-information --profile smarttraffic-project --region us-east-2 --filters "Key=InstanceIds,Values=$authId" --query 'InstanceInformationList[].{Id:InstanceId,SSM:PingStatus}' --output table
aws ssm start-session --profile smarttraffic-project --region us-east-2 --target $authId
```

Después iniciar su Compose y validar. Repetir cambiando Zone para data, app, core,
ingest. No encender las cinco simultáneamente y dar por comprobadas sus dependencias.

## Créditos, factura y domingo 22

Abrir **Billing and Cost Management → Credits → crédito individual**: revisar
saldo, fecha de vencimiento, servicios aplicables y detalles de compartición.
Luego **Bills → mes de noviembre de 2026**: revisar uso/cargos por servicio y
región, sección de créditos e impuestos. Billing es global; la región de EC2
no cambia la cuenta de facturación. Los importes pueden actualizarse con demora.
No activar Cost Explorer, informes, exportaciones ni productos de pago para leer
estos datos. Si hay AccessDenied, solicitar la vista al administrador de Billing;
no ampliar permisos automáticamente.

El domingo: guardar evidencia y respaldo local necesario; detener aplicaciones
y EC2; comprobar Stopped; revisar recursos residuales. El corte automático
preparado hace **Stop**, no borra discos. Si ya no se necesita infraestructura,
seguir [TEARDOWN.md](TEARDOWN.md) con una **autorización de borrado separada**.

| Acción | Efecto | ¿Sigue EBS? |
|---|---|---|
| Stop instance | Apaga cómputo; permite volver a arrancar | Sí |
| Terminate instance | Elimina esa VM de forma irreversible | En este proyecto sí: delete_on_termination=false |
| terraform destroy | Elimina recursos administrados por ese state, incluidas red/IAM/VMs | Los discos retenidos pueden quedar fuera del state; revisar y eliminar aparte si se autoriza |

Referencias: [detener/arrancar EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Stop_Start.html),
[créditos](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/useconsolidatedbilling-credits.html),
[terminación y EBS](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/terminating-instances.html).
