# Eliminación definitiva al finalizar el proyecto

**Guía preparada, no ejecutada.** Destruir elimina VMs/red/IAM y puede causar
pérdida de datos. Eliminar EBS o snapshots es irreversible. Hace falta autorización
explícita de borrado, posterior a la aprobación de despliegue/parada. No hay una
tarea automática de eliminación programada para el domingo.

## Antes de eliminar

1. Confirmar que ya no se necesita SmartTraffic, fecha y cuenta/rol correctos.
2. Detener la ingesta y aplicaciones como indica [POWER-GUIDE.md](POWER-GUIDE.md).
3. Guardar evidencia, versiones de imágenes y estado Terraform en almacenamiento
   **local protegido**, fuera de Git. Resguardar solo los secretos necesarios para
   recuperar; nunca entregar CA privada o JWT privado a todo el equipo.
4. Decidir qué datos se conservarán. Un snapshot EBS permanece facturable y puede
   requerir autorización adicional; no se crea como supuesto «backup gratis».
   Para BD, un pg_dump local protegido y verificado evita retener un disco AWS
   solo por el respaldo. Validar restauración antes de borrar el original.
5. Anotar los IDs de las cinco EC2 y **sus VolumeId**, tags/zonas, snapshots, VPC e
   interfaces. La retención configurada deja EBS después de terminar instancias.
6. Acordar por escrito qué discos/snapshots concretos se pueden borrar. No borrar
   todos los recursos de la región ni la VPC predeterminada.

Inventario de lectura, antes y después de destruir:

```powershell
aws sts get-caller-identity --profile smarttraffic-project --region us-east-2
aws ec2 describe-instances --profile smarttraffic-project --region us-east-2 --filters Name=tag:Project,Values=smarttraffic Name=tag:Environment,Values=lab --query 'Reservations[].Instances[].{Id:InstanceId,State:State.Name,Disks:BlockDeviceMappings[].Ebs.VolumeId}' --output json
aws ec2 describe-volumes --profile smarttraffic-project --region us-east-2 --filters Name=tag:Project,Values=smarttraffic Name=tag:Environment,Values=lab --query 'Volumes[].{Id:VolumeId,State:State,GiB:Size,Attached:Attachments[].InstanceId}' --output table
aws ec2 describe-snapshots --profile smarttraffic-project --region us-east-2 --owner-ids self --query 'Snapshots[].{Id:SnapshotId,Disk:VolumeId,GiB:VolumeSize}' --output table
```

No usar los resultados de snapshots sin tags para autorizar borrado por sí solos:
comparar con los IDs registrados y la decisión de conservación.

Para un respaldo **local**, si se dispone de pg_dump 16 en el equipo, abrir un
túnel SSM a PostgreSQL de vm-data. No abrir 5432 al público ni entregar la clave
privada del servidor. El nombre verificado es su IP SAN; hostaddr apunta al túnel:

```powershell
# Terminal 1, ID real de vm-data; requiere autorización de sesión.
aws ssm start-session --profile smarttraffic-project --region us-east-2 --target i-IDDATA --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["5432"],"localPortNumber":["15432"]}'
```

```powershell
# Terminal 2; ruta de CA pública real, sin contraseña en argumentos.
# pg_dump solicita la contraseña del rol migrator de forma interactiva.
pg_dump 'host=10.42.10.11 hostaddr=127.0.0.1 port=15432 dbname=smarttraffic user=smarttraffic_migrator sslmode=verify-full sslrootcert=C:/RUTA-PROTEGIDA/ca.pem' -W -Fc -f .tools/smarttraffic-final.dump
if ($LASTEXITCODE -ne 0) { throw 'Respaldo incompleto: no autorizar borrado' }
pg_restore --list .tools/smarttraffic-final.dump
```

La carpeta local debe tener ACL restrictiva. Listar el dump no equivale a probar
su restauración: restaurarlo en una **base nueva aislada**, validar datos y luego
autorizar el borrado de la original. No usar pg_restore --clean contra una base
existente. Si no se tiene pg_dump, instalar el cliente requiere autorización local;
no retener un snapshot facturable sin acordarlo ni improvisar una copia por logs SSM.

## Opción preferida: Terraform

Usar **el state exacto** con el que se aplicó el laboratorio; conservar el backend
y lockfile. Si no existe/no se encuentra, parar y reconstruir inventario; no crear
otro state vacío y suponer que `destroy` elimina la cuenta.

Con perfil temporal, sin variables de credenciales de otra cuenta:

```powershell
$env:AWS_PROFILE = 'smarttraffic-project'
$env:AWS_REGION = 'us-east-2'
.tools/terraform/terraform.exe -chdir=infra/aws plan -destroy -out=teardown.tfplan
.tools/terraform/terraform.exe -chdir=infra/aws show teardown.tfplan
```

Revisar listado exacto, datos a conservar y dependencias. **Tras autorización
explícita para ese plan de borrado**, ejecutarlo con Terraform apply del plan de
destrucción. Alternativamente `terraform destroy` prepara y solicita confirmación
interactiva; no usar `-auto-approve`. Ambos eliminan solo lo administrado en state.
La autorización para un apply de creación no autoriza este apply de borrado.

Conservar logs de eliminación y state final sin secretos en la documentación
compartible. **Después de destroy, volver a consultar EBS**: los root disks tienen
delete_on_termination=false y pueden quedar como `available`, fuera del state.

## Eliminar discos retenidos con consola

Solo después de copia/restauración comprobada y autorización de esos discos:

1. EC2 → **Elastic Block Store → Volumes**, región Ohio.
2. Filtrar Project=smarttraffic, Environment=lab; comparar VolumeId con el inventario.
3. Deben estar **Available**, sin Attachments. Si siguen In-use, no forzar detach.
4. Seleccionar únicamente los discos aprobados → **Actions → Delete volume**.
5. Leer la advertencia de pérdida permanente y confirmar esos IDs.
6. Actualizar hasta que desaparezcan. Repetir consulta de tags y revisar también
   discos sin tags relacionados con los VolumeId registrados.

CLI equivalente, **para un ID específico previamente aprobado**, nunca un bucle
que borre todos los volúmenes de la cuenta:

```powershell
aws ec2 describe-volumes --profile smarttraffic-project --region us-east-2 --volume-ids vol-IDAPROBADO
# Solo si Available, pertenece al laboratorio y tiene autorización:
aws ec2 delete-volume --profile smarttraffic-project --region us-east-2 --volume-id vol-IDAPROBADO
```

## Si se elimina manualmente mediante consola

Terraform es preferible para no dejar red/roles huérfanos. Si se autorizó un
borrado manual, registrar los cambios y reconciliar el state posteriormente:

1. EC2 → Instances → verificar las cinco por IDs/tags → **Terminate instance**.
   Una instancia terminada no puede reiniciarse; necesitaría una nueva y restauración.
2. Esperar Terminated. Revisar/borrar EBS retenidos por el procedimiento anterior.
3. Revisar snapshots y AMIs propias. Conservar solo los acordados; AMI y snapshots
   son recursos distintos. No eliminar snapshots compartidos/ajenos sin autorización.
4. Revisar Elastic IPs y liberar únicamente las propias del laboratorio si existen.
   El plan no crea EIP: no liberar las de otra aplicación. Las IPv4 automáticas
   desaparecen con Stop/Terminate y no necesitan `release-address`.
5. En VPC, eliminar solo la VPC dedicada SmartTraffic y sus dependencias: reglas/SG,
   asociación/ruta/subred/IGW. No eliminar VPC default ni interfaces de otros servicios.
6. En IAM, retirar solamente los cinco instance profiles/roles smarttraffic-lab-* y
   sus attachments creados por Terraform. No eliminar el rol de acceso del usuario,
   AWS Organizations, SCP, roles service-linked ajenos ni modificar el plan.

## Cierre y recursos residuales

Repetir [COSTS.md](COSTS.md). Revisar EBS, snapshots, EIP, NAT, endpoints, ELB,
RDS/MQ/MSK, S3/logs si se crearon fuera del plan, y otras regiones autorizadas.
Una SCP deniega lecturas fuera de Ohio: registrar esa limitación y pedir al
administrador un inventario desde una identidad ya autorizada, sin cambiar SCP.

En Billing → Bills revisar cargos/créditos e impuestos del mes y reconsultar al
actualizarse los datos. El borrado detiene consumo futuro de los recursos eliminados;
no borra la factura ya acumulada. No declarar saldo/factura cero a partir de una
lista EC2 vacía. No cerrar la cuenta, abandonar la organización ni borrar backups
locales como parte implícita de este procedimiento.

Referencias: [terminar EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/terminating-instances.html),
[eliminar EBS](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-deleting-volume.html),
[Terraform destroy](https://developer.hashicorp.com/terraform/cli/commands/destroy).
