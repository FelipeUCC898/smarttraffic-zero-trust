# SmartTraffic — revisión local y preparación AWS

**Entrega actual:** [RELEASE-READINESS.md](RELEASE-READINESS.md). Docker iniciado;
flujo real, seguridad de brokers/BD, WebSocket, alerta, renovación corta, roles SQL
y recuperación comprobados localmente. Imágenes exportadas y CA nueva separada
por zona bajo `.tools`, fuera de Git. Plugin SSM instalado con autorización.
Plan final esperado 53/0/0, sin aplicar, con cutoff al finalizar el domingo 22.
El propietario acepta evaluar uso de créditos; falta comprobar cobertura/vigencia
en Billing y autorizar explícitamente apply. Lo que sigue conserva el diagnóstico
anterior; sus bloqueos locales ya resueltos no describen el estado de entrega actual.

**Actualización autenticada:** [FEASIBILITY.md](FEASIBILITY.md) confirma PAID/ACTIVE,
USD 100 y Organizations existente, con plan nuevo de 53 creaciones sin aplicar.
Las afirmaciones de perfil ausente y saldo no verificable que siguen describen
la revisión anterior; no representan el estado actual. No se autorizó consumo.

Actualización de alcance: el usuario exige **solo recursos de la cuenta gratuita**.
Ver [FREE-PLAN.md](FREE-PLAN.md). Free Plan no implica ausencia de consumo de
créditos; ese consumo sigue pendiente de confirmar con saldo y ventana concretos.
No hay autorización para cambiar a Paid Plan.

Fecha: **2026-11-16**, America/Bogota. Rama:
`dev-codex/feat-aws-readiness`, basada en `origin/develop` **72e69cb**.
Worktree: `.worktrees/aws-readiness`. La rama original
`dev-dos/feat-domain-messaging` y sus cambios no confirmados se preservan.
`git fetch origin --prune` completado; `3291eeb` y `a205942` son ancestros de
`origin/develop` (PR #20 fusionada, comprobado por historial).

## Estado de entrega

| Elemento | Estado y evidencia de esta revisión |
|---|---|
| Instalación reproducible de Compose | IMPLEMENTADO: release oficial 5.1.3, SHA-256 fijado, instalación global, comprobación de versión |
| Firewall y apagado | PREPARADO PARA AWS: Docker requiere unidad firewall; timer systemd antes de descargas, apagado desde cada arranque, EC2 `stop` explícito |
| Terraform | VALIDADO LOCALMENTE: init con proveedor firmado, fmt, validate y 4 planes con proveedor simulado; **sin plan autenticado nuevo** |
| Cinco zonas Compose | VALIDADO LOCALMENTE: Test-Zones con plantilla pública y solo variables necesarias por VM; no contenedores arrancados |
| Java | VALIDADO LOCALMENTE: 403 pruebas ejecutadas sin fallos; 10 omitidas por requerir servicios externos |
| Auth HTTPS real | VALIDADO LOCALMENTE: servidor HTTPS local de Java, JWT verificado con CA confiada y rechazo sin esa confianza; sin Docker |
| Exportación de imágenes | IMPLEMENTADO: archivos por zona, IDs Linux/amd64 y SHA-256; pruebas con Docker simulado. Exportación/carga real PENDIENTE |
| IAM temporal | PREPARADO: guía y política de lectura; creación de usuario, MFA, login y aceptación por IAM PENDIENTES |
| AWS | NO DESPLEGADO; ninguna modificación ni consumo autorizado de créditos |
| Datos entre brokers/BD | PENDIENTE de repetir en esta estación: motor Docker detenido/no disponible |
| Pruebas EC2 | PENDIENTES: bootstrap, nftables, SSM, certificados, redes, persistencia y apagado reales |

Los resultados de [VALIDATION.md](VALIDATION.md) corresponden a una revisión
anterior; no son evidencia de una sesión AWS o integración activa aquí.

## Herramientas y recursos observados

- Git disponible; AWS CLI **2.34.49**; Docker CLI **29.4.3**; Compose **5.1.3**.
- Java **17.0.12** y Maven **3.9.11** ya instalados.
- Instalados portátiles, sin cambiar PATH global: Terraform **1.13.5** y PowerShell
  **7.4.13**, en `.tools/terraform` y `.tools/pwsh`. Descargas oficiales comprobadas
  contra SHA-256 publicado por HashiCorp y PowerShell.
- Proveedor AWS **6.73.0**, firma HashiCorp verificada. Se añadió al lockfile el
  checksum `h1` de Windows, conservando los hashes anteriores y la misma versión.
- OpenSSL existente en Git for Windows se utilizó para fixtures HTTPS descartables.
- Memoria visible: 16,053,040 KiB (~15.31 GiB), libre 1,994,692 KiB (~1.90 GiB)
  al inspeccionar. No se inició un stack completo bajo esa presión de memoria.
- Docker Desktop context `desktop-linux` existe, pero su named pipe no responde.
  No se detuvieron contenedores ni se cambiaron contextos/configuraciones globales.

## Pruebas y reproducción

| Módulo | Ejecutadas | Omitidas | Fallos/errores |
|---|---:|---:|---:|
| service-security | 4 | 0 | 0 |
| auth-server | 7 | 8 | 0 |
| alert-dispatcher | 4 | 0 | 0 |
| query-client | 3 | 0 | 0 |
| traffic-dashboard | 2 | 0 | 0 |
| traffic-archiver | 7 | 2 | 0 |
| density-processor | 308 | 0 | 0 |
| traffic-sensor-simulator | 68 | 0 | 0 |
| **Total** | **403** | **10** | **0** |

Evidencia de Java: `*/target/surefire-reports/TEST-*.xml`; logs de siete módulos
en `.tools/*-test.log`. `service-security` se instaló primero en `.tools/m2` para
los clientes que dependen de él. Después se habilitó y ejecutó su prueba HTTPS
antes omitida mediante [Test-Https.ps1](../local-security/Test-Https.ps1).
Las ocho pruebas RabbitMQ de auth y dos PostgreSQL del archivador siguen omitidas.
No contar las 10 pruebas Kafka/PG históricas como ejecutadas en esta sesión.

Desde este worktree:

```powershell
.tools/terraform/terraform.exe -chdir=infra/aws fmt -check -recursive
.tools/terraform/terraform.exe -chdir=infra/aws validate -no-color
.tools/terraform/terraform.exe -chdir=infra/aws test -no-color
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Test-Preparation.ps1
.tools/pwsh/pwsh.exe -NoProfile -File infra/aws/Test-Zones.ps1 -FixtureEnv infra/aws/zones/runtime.env.example
.tools/pwsh/pwsh.exe -NoProfile -File infra/local-security/Test-Https.ps1 -OpenSslCommand 'C:\Program Files\Git\usr\bin\openssl.exe'
```

Los tests Terraform usan `mock_provider`: no solicitan credenciales ni crean
recursos. Comprueban cero EC2 por defecto, cinco VMs cuando se solicita plan de
revisión, IMDSv2, EBS cifrado/retenido, SG por puertos TLS, T3 standard, timer y
duraciones inválidas. El bootstrap renderizado y el importador pasan `bash -n`.
Test-Preparation verifica rechazo de root/cuenta incorrecta antes de otras APIs,
restauración de entorno, argumentos de inventario y bundles de imágenes con mocks.
`git diff --check` completado sin errores.

Test-EndToEnd se intentó: falla en el nuevo preflight por falta de motor Linux,
antes de generar credenciales o entrar en cleanup. No se afirma circulación
real Simulador → Kafka → PostgreSQL o Kafka → RabbitMQ en esta revisión.
Cuando haya motor Linux y memoria suficiente, usar el laboratorio TLS existente
en [local-integration](../local-integration/README.md). Ese laboratorio mantiene
servicios separados, pero no representa aislamiento de cinco EC2.

## Acceso y presupuesto

Estado comprobado localmente: únicamente perfil `default`, región `us-east-1`,
sin parámetros SSO; `smarttraffic-project` no existe. No se leyeron/imprimieron
credenciales ni se modificó `default`. STS del perfil solicitado falló por perfil
ausente. No es posible enumerar usuarios IAM ni inventariar AWS sin acceso.

El propietario comunicó Free Plan y USD 100.00 disponibles; vencimiento y
Organizations desconocidos. Mantener **SSO/Organizations/Paid Plan deshabilitados**.
Seguir [ACCESS.md](ACCESS.md): usuario IAM individual de consola con MFA,
política de lectura y `aws login` para credenciales temporales. Se requiere
intervención humana en la consola y el navegador; no compartir secretos por chat.

El inventario [Get-Readiness.ps1](Get-Readiness.ps1) rechaza root y otra cuenta,
solo consulta APIs de lectura y no usa Cost Explorer. Aún no se ejecutó con una
sesión real. Los permisos JSON están preparados y parseados, no validados por IAM.
No se creó presupuesto ni se enviaron notificaciones. Preparar una alerta de
costo cero desde Billing, con correo real del propietario y sin acciones de pago;
comprobar su entrega antes de cualquier despliegue. No es un límite de gasto.

No se ha comprobado cobertura gratuita de las cinco instancias previstas. Las
tarifas históricas del README darían aproximadamente USD 205.20 por 730 horas,
incluidos cinco IPv4 y 120 GiB gp3, **antes de beneficios y excluyendo tráfico,
snapshots, logs e impuestos**. No es una cotización actual ni autorización para
usar créditos. Reconsultar Price List para Ohio y condiciones de Free Tier antes
de presentar un plan. IPv4 públicas tienen tarifa base USD 0.005/IP/h según
[AWS VPC Pricing](https://aws.amazon.com/vpc/pricing/).

Recursos definidos a revisar: VPC/subred/IGW/rutas, cinco SG y reglas mínimas,
cinco roles/perfiles IAM SSM, y opcionalmente cinco EC2 con EBS gp3 cifrado.
No hay NAT Gateway, EIP ni endpoints privados. EC2, IPv4, EBS y tráfico pueden
consumir beneficios o facturarse. Un plan sin EC2 todavía contiene infraestructura.
No se crearon, arrancaron, detuvieron ni destruyeron recursos AWS. Inventario
existente, saldo por API y créditos utilizados: **no verificables aún**.

## Imágenes y entrega

Inventario **declarado**, no de imágenes presentes o publicadas:

| Zona | Referencias |
|---|---|
| auth | `smarttraffic/auth-server:aws-lab` |
| data | `confluentinc/cp-kafka:7.6.1`, `rabbitmq:3.13-alpine`, `postgres:16-alpine` |
| ingest | `smarttraffic/traffic-sensor-simulator:aws-lab` |
| core | `smarttraffic/density-processor:aws-lab`, `smarttraffic/traffic-archiver:aws-lab` |
| app | `smarttraffic/alert-dispatcher:aws-lab`, `smarttraffic/query-client:aws-lab`, `smarttraffic/traffic-dashboard:aws-lab` |

Construir en una máquina local con recursos suficientes, nunca en las VMs pequeñas.
Tras revisar fuente e imágenes: `Export-Images.ps1 -Zone app -OutputDirectory
.tools/bundles/app` (repetir por zona). Registra IDs inmutables y SHA-256 del tar y
manifiesto. Entregar bundle y checksum por canal autenticado ya aprobado y
presupuestado. `bash Import-Images.sh app /ruta/bundle` comprueba integridad,
carga y verifica IDs antes de arrancar servicios. Los hashes no autentican por
sí solos al emisor ni demuestran correspondencia entre imagen y fuente.
No se ha contratado registry, S3 ni mecanismo de transferencia cloud.

## Certificados, permisos y operación pendientes

No se generó CA de despliegue ni se reutilizaron certificados de prueba.
La matriz de SAN y montajes individuales está en [zones/README.md](zones/README.md).
CA privada offline; clave JWT privada solamente auth; claves de brokers/PG
solamente data. Para aplicaciones no-root, usar ACL/propietarios adecuados al
UID real de la imagen, archivos privados 0600 o 0640 con grupo específico y
directorio de secretos 0700. Nunca chmod 644 de claves de despliegue. Los fixtures
HTTPS de `.tools/https-security` son descartables y no se distribuyen.

UID declarados en Dockerfiles: archivador/simulador/procesador 10001,
dashboard 10002, dispatcher 10003, query 10004. Auth usa `appuser` sin UID fijo:
inspeccionar su imagen real y los UIDs Kafka/RabbitMQ antes de entregar secretos.
No se han validado propietarios/ACL en Linux ni truststores de despliegue.

PostgreSQL continúa usando el usuario bootstrap superusuario en este laboratorio.
**Puerta de seguridad pendiente:** separar `smarttraffic_migrator` (LOGIN,
sin SUPERUSER/CREATEROLE/CREATEDB, propietario de tablas/esquema de aplicación)
y `smarttraffic_archiver` (CONNECT, USAGE y SELECT/INSERT sobre
`traffic_observation`; sin DDL ni privilegios de administración). Revisar SQL
emitido por JPA antes de añadir UPDATE u otros permisos. Configurar las
credenciales Flyway separadas del datasource y comprobar rechazo de DDL con el
usuario de ejecución. Mantener bootstrap fuera de core y probar migración y
persistencia con TLS en base nueva y existente antes de cambiar credenciales.
No se aplicaron cambios SQL sin una BD para comprobarlos.

Rotación JWT actual: el `kid` está fijado a `smarttraffic-rs256-v1` en emisor y
RabbitMQ. No prometer rotación sin interrupción. Preparar una ventana coordinada:
detener ingest/core/app, drenar trabajo, renovar par en auth (privada solo allí),
reemplazar pública en Rabbit y aplicaciones, reiniciar auth/Rabbit, comprobar
JWKS/caché de Kafka y esperar el vencimiento de tokens antiguos. Reiniciar
app → core → ingest; comprobar emisión, firma, Kafka y Rabbit y rechazo de clave
anterior. Conservar rollback protegido. Rotación gradual requiere implementar
`kid` configurable y coexistencia de claves, actualmente pendientes.

Tras una futura autorización de despliegue: auth → validar HTTPS/JWKS → data →
salud e inicialización Kafka/ACL → app → core → ingest, con archivos por VM,
`up -d --no-build --pull never` y pruebas de eventos nuevos. Para apagar aplicaciones,
`docker compose --env-file /secure/ZONA.env -f ZONA.yml stop`, preferentemente
ingest → core → app → data → auth. No usar `down -v`.
El timer `smarttraffic-lab-stop.timer` está preparado, no probado en EC2; comprobar
`systemctl list-timers` y apagado real en la ventana autorizada. Un stop de EC2 no
elimina EBS ni snapshots y el timer no reemplaza control de facturación.

Pruebas funcionales pendientes con servicios: persistencia reciente, consultas y
correlation IDs, WebSocket, alertas, JWT/ACL/TLS negativos de brokers, reinicios,
renovación de tokens y recuperación. Rutas sobre grafo y Observer siguen pendientes.
No se encontró el enunciado del taller en el árbol de develop; no se inventaron
requisitos adicionales ni datos de negocio.

## Git y revisión

No se hizo commit en develop/master ni se modificaron archivos originales del
equipo. Los commits de esta rama se consultan con
`git log --oneline origin/develop..HEAD`; lista de archivos con
`git diff --name-only origin/develop...HEAD`. [PR.md](PR.md) contiene el cuerpo
preparado hacia `develop`; PR remota no creada ni rama publicada.
No se incluyen `.env`, claves, certificados privados, planes, estados ni caches.
