# Desplegar SmartTraffic después de la autorización

Estado: preparación local. **No se ha ejecutado apply ni instalado automatización
en AWS.** Cuenta 223455088753, perfil smarttraffic-project, Ohio us-east-2.
El plan PAID/ACTIVE y Organizations se conservan. No usar root ni claves permanentes.

## Decisión y ventana

El propietario acepta estudiar el uso de USD 100 promocionales, sin cargos
adicionales. Todavía debe comprobar su vigencia/cobertura y autorizar el plan.
Fin propuesto: **domingo 22 de noviembre de 2026 a las 23:59, Bogotá**; el corte
técnico se prepara para **23 de noviembre 00:00 Bogotá = 05:00 UTC**.
No es una extensión a la mañana del lunes. Ver [COSTS.md](COSTS.md).

Recursos: VPC/subred/IGW/rutas; cinco security groups con sus reglas; cinco
roles/perfiles SSM y attachments; cinco EC2 con 120 GiB gp3 cifrado e IPv4
automática. Total del plan esperado: 53 creaciones. Sin NAT, EIP, balanceador,
registro de imágenes, almacenamiento S3 ni bases/brokers administrados.

| VM / nombre EC2 | IP privada | Tipo | Disco | Aplicaciones |
|---|---|---|---:|---|
| vm-auth | 10.42.10.10 | t3.small | 16 GiB | Auth HTTPS/JWKS |
| vm-data | 10.42.10.11 | m7i-flex.large | 40 GiB | Kafka, RabbitMQ, PostgreSQL |
| vm-ingest | 10.42.10.12 | t3.small | 16 GiB | Simulador |
| vm-core | 10.42.10.13 | c7i-flex.large | 24 GiB | Procesador, archivador |
| vm-app | 10.42.10.14 | t3.small | 24 GiB | Dispatcher, query-client, dashboard |

No hay SSH ni puertos de aplicación públicos. Las IPv4 proporcionan salida para
SSM/HTTPS; los clientes usan las IP privadas con SAN verificados y puertos TLS.
Una sola AZ, KRaft de un nodo, sin alta disponibilidad. No reducir data a micro.

## Antes de solicitar apply

1. Verificar en Billing → Credits los detalles indicados en COSTS.md y confirmar
   un saldo utilizable para esta cuenta hasta el cierre. Detenerse si el crédito
   no cubre EC2/EBS/IPv4 o vence antes. Revisar también gastos ajenos/compartidos.
2. Revisar evidencia local en [RELEASE-READINESS.md](RELEASE-READINESS.md), imágenes
   y archivos generados. Las pruebas locales no prueban SSM/firewall en EC2.
3. Revisar límites PostgreSQL, distribución de claves y cierre del domingo.
4. Preparar alerta de gasto desde Billing, sin habilitar acciones de pago. No se
   ha creado ni enviado una alerta: requiere aprobación de escritura y correo real.
   No sustituye un límite de gasto.
5. Generar plan nuevo con el helper. No reutilizar un plan de otra estación ni
   un plan anterior a cambios de bootstrap. Verificar STS antes de cualquier acción.

```powershell
# Desde .worktrees/aws-readiness
aws sts get-caller-identity --profile smarttraffic-project --region us-east-2
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Invoke-Terraform.ps1 -Action plan -CreateInstances -TerraformPath .tools/terraform/terraform.exe
```

El helper solo prepara; no admite apply/destroy. EC2 está deshabilitado por defecto
en Terraform, pero el plan revisado con CreateInstances incluye las cinco VMs.
Si se aprueba ese plan, aplicar con Terraform nativo bajo **el mismo perfil temporal**
en una sesión sin credenciales AWS de otra identidad en variables de entorno:

```powershell
# SOLO después de la aprobación explícita del plan/costo/ventana.
$env:AWS_PROFILE = 'smarttraffic-project'
$env:AWS_REGION = 'us-east-2'
.tools/terraform/terraform.exe -chdir=infra/aws apply lab.tfplan
```

El provider restringe cuenta/región. Si las credenciales exportadas por el helper
no son accesibles al provider en modo perfil, usar una exportación **solo al entorno
del proceso**, como hace Invoke-Terraform.ps1; no guardarlas en archivos/comandos.
No sustituir errores de sesión por credenciales raíz. Conservar state local protegido
y una copia cifrada fuera de Git; perderlo dificulta el borrado controlado.

## Certificados y secretos preparados localmente

`Prepare-Deployment.py` genera CA nueva, SAN de las IP privadas, certificados
de servidor, Kafka con EKU server/client, JWT RS256, truststore PKCS12 y
credenciales distintas. Windows: retira herencia de ACL del directorio y concede
acceso al propietario del repositorio y al usuario ejecutante específico; Linux:
directorios 0700 y archivos 0600. No imprime valores.
Solo admite un directorio NUEVO dentro de `.tools` y no llama AWS.

```powershell
python infra/aws/Prepare-Deployment.py --output .tools/aws-deployment-AAAAMMDD --openssl 'C:\Program Files\Git\usr\bin\openssl.exe'
```

Se preparó `.tools/aws-deployment-20261116` en esta estación. No regenerar su CA
para una VM aislada: produciría cadenas incompatibles. Certificados de servidor
válidos 14 días desde generación; CA 30 días. Comprobar fecha real antes de enviar.
`distribution.json` contiene solo nombres de archivos/variables por VM.

- `OFFLINE-DO-NOT-UPLOAD` permanece en el equipo protegido; **nunca se entrega**.
- JWT privado y Auth PKCS12 solo en auth. Claves de brokers/BD solo en data.
- Core recibe credenciales de ejecución/migración, nunca la de bootstrap. La
  variable POSTGRES_PASSWORD de base queda con valor ficticio en core y se
  reemplaza mediante core-roles.yml; siempre usar ese overlay.
- Clientes solo reciben confianza pública y su credencial. Un truststore aquí
  contiene la CA pública, no claves privadas.
- RabbitMQ fija la clave JWT pública: rotación coordinada, no automática.

## Imágenes y entrega sin contratar servicios adicionales

Construir localmente. `Export-Images.ps1` guarda por VM un tar de imágenes y
manifiesto con IDs SHA256 verificables; nunca exporta variables de configuración.
Las etiquetas aws-lab son locales: no intentar descargarlas desde Docker Hub.

```powershell
.tools/pwsh/pwsh.exe -File infra/aws/Export-Images.ps1 -Zone auth -OutputDirectory .tools/aws-deployment-20261116/auth/images
# Repetir para data, ingest, core y app con su directorio correspondiente.
```

La entrega preparada usa **SSM port forwarding + receptor HTTP en loopback**.
El contenido viaja por el túnel autenticado/cifrado de SSM, con token de una sola
entrega y SHA256 fijado. No expone un puerto público, no abre SSH y no crea bucket
ni registry. Los extremos localhost y administradores del equipo/VM son de confianza.
El receptor acepta solo archivos/directorios dentro de un destino nuevo root-only;
rechaza rutas de escape y enlaces. Se cierra tras una entrega o 30 minutos.

Después del apply aprobado, obtener el ID de cada VM del output Terraform y
comprobar el tag Zone. Estos pasos son para esa futura ventana, **no ejecutados**:

```powershell
python infra/aws/Prepare-Transfer.py --deployment .tools/aws-deployment-20261116 --zone auth --instance-id i-IDREAL
# Genera localmente uploads/auth/zone.tar, token.txt y ssm-receiver.json.
# Revisar el JSON (solo código público + hashes; no incluye el token).
aws ssm send-command --profile smarttraffic-project --region us-east-2 --cli-input-json file://.tools/aws-deployment-20261116/uploads/auth/ssm-receiver.json
```

Conectar SSM → Run Command y comprobar Success; el receptor se ejecuta en segundo
plano. El bootstrap incluye python3; comprobar su instalación real en EC2:
un plan no prueba la descarga/instalación de paquetes. Comprobar espacio
libre para tar de entrega + tar de imágenes + capas antes de transferir.
El plugin Session Manager debe estar instalado en el equipo para el túnel.

```powershell
# Terminal 1, mantener abierta. Reemplazar el ID.
aws ssm start-session --profile smarttraffic-project --region us-east-2 --target i-IDREAL --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["18800"],"localPortNumber":["18800"]}'
```

```powershell
# Terminal 2, PowerShell 7; nunca imprimir token.txt.
$upload = '.tools/aws-deployment-20261116/uploads/auth'
$token = (Get-Content "$upload/token.txt" -Raw).Trim()
Invoke-WebRequest -Method Post -Uri http://127.0.0.1:18800/bundle -Headers @{Authorization="Bearer $token"} -InFile "$upload/zone.tar" -ContentType application/octet-stream -TimeoutSec 1800
Remove-Variable token
```

Repetir secuencialmente por VM. Ante fallo, inspeccionar destino parcial y logs;
no sobrescribir ni borrar automáticamente una entrega anterior.
Desde sesión administrativa SSM en la VM correcta (comandos root):

```bash
cd /opt/smarttraffic/incoming/auth
bash Import-Images.sh auth /opt/smarttraffic/incoming/auth/images
bash Install-Zone.sh auth /opt/smarttraffic/incoming/auth
```

El bundle de configuración incluye `Install-Zone.sh` y el importador de imágenes
[Import-Images.sh](Import-Images.sh).
Ejecutar `bash Import-Images.sh auth /opt/smarttraffic/incoming/auth/images`,
luego `bash Install-Zone.sh auth /opt/smarttraffic/incoming/auth`. Para cada zona,
usar su nombre. El instalador valida hostname, permisos, imagen UID y Compose;
rechaza sobreescribir una instalación y no arranca aplicaciones.

## Arranque con verificación por etapas

En cada host, usar `/opt/smarttraffic/runtime.env`, configuración dentro de
`/opt/smarttraffic/repo/infra/aws/zones`, `--no-build --pull never`. **Nunca el
Compose general de desarrollo**. En data y core añadir sus overlays `*-roles.yml`.

1. Comprobar cloud-init, SSM, `docker compose version`, disco y `nft list ruleset`.
   `systemctl list-timers 'smarttraffic-lab-*'` debe mostrar el límite por arranque
   y el calendario del domingo. IMDSv2 y salida metadata solo root.
2. Auth: `up -d --no-build --pull never`; comprobar HTTPS/JWKS desde una VM cliente
   con `curl --cacert ... https://10.42.10.10:8443/.well-known/jwks.json`. Sin `-k`.
3. Data: `up -d --no-build --pull never --wait --wait-timeout 180`.
   Ejecutar explícitamente dentro de postgres `sh /run/config/configure-postgres-roles.sh`
   antes de core. Crea/restringe roles, fija passwords entregados y cambia propietario
   del esquema/tablas del laboratorio; no usar sobre otra BD. En una BD existente,
   respaldar y coordinar parada del archivador primero.
4. Data/Kafka: `exec -T kafka bash /run/config/kafka-init.sh`; revisar topic/ACL.
5. App: arrancar consumidores; esperar conexiones RabbitMQ y query HTTP.
6. Core con core-roles.yml: arrancar archivador/procesador, esperar migraciones y
   conexión a Kafka. Flyway usa migrator; datasource usa archiver (SELECT/INSERT,
   sin DDL/UPDATE/DELETE). Core conserva credencial de migración para arranque:
   esta separación no aísla un contenedor comprometido del permiso DDL de migrator.
   Una fase de migración independiente y retirada posterior de esa credencial es
   una mejora pendiente para un despliegue de producción.
7. Ingest: arrancar simulador al final. Registrar timestamp de inicio y filas nuevas.
8. Pasar todas las verificaciones de [POST-DEPLOY.md](POST-DEPLOY.md) y guardar
   evidencia sin tokens/contraseñas. No declarar funcionalidad solo por estado Up.

Ejemplo data (solo en VM data y tras aprobación):

```bash
cd /opt/smarttraffic/repo
docker compose --env-file /opt/smarttraffic/runtime.env -f infra/aws/zones/data.yml -f infra/aws/zones/data-roles.yml up -d --no-build --pull never --wait --wait-timeout 180
docker compose --env-file /opt/smarttraffic/runtime.env -f infra/aws/zones/data.yml -f infra/aws/zones/data-roles.yml exec -T postgres sh /run/config/configure-postgres-roles.sh
docker compose --env-file /opt/smarttraffic/runtime.env -f infra/aws/zones/data.yml -f infra/aws/zones/data-roles.yml exec -T kafka bash /run/config/kafka-init.sh
```

Para dashboard/query, SSM forwarding a 8081/8092 en vm-app; abrir localhost en el
equipo. Acceso de navegador está limitado al túnel administrativo; no es un
dashboard público con login de usuario. Si falla una etapa, parar ingest primero
y corregirla; no añadir puertos públicos ni deshabilitar TLS como solución.
