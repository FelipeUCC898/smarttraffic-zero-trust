# Entrega de preparación y pruebas — 16 de noviembre de 2026

Rama `dev-codex/feat-aws-readiness`, base develop `72e69cb`. Se conservaron la
rama original y sus cambios ajenos. **AWS no desplegado**: solo se ejecutaron
consultas de lectura y planes; no apply/destroy, StartInstances, cambios de plan,
Organizations/SCP, presupuestos ni consumo de créditos por cargas de este trabajo.

## Pruebas completadas en esta estación

| Prueba | Resultado observado |
|---|---|
| Suites Java de revisión anterior | 402 ejecutadas sin fallos; HTTPS pasó aparte; no se repitieron innecesariamente todas |
| Kafka/PostgreSQL real | 10/10, incluidos JWT/ACL/TLS/hostname/plaintext negativos |
| Simulador → Kafka → archivador → PostgreSQL | 18 observaciones nuevas en la primera ejecución completa |
| Kafka → procesador → RabbitMQ → dispatcher/query | Respuesta OK con estado posterior al T0 de esa ejecución |
| Dashboard WebSocket y alerta | 1 prueba ampliada correcta: estado nuevo por socket y CONGESTION entregada a un consumidor Rabbit real |
| Renovación corta | En esa prueba, estado/consulta/persistencia correctos después de expirar el JWT inicial de 60 s |
| RabbitMQ seguridad real | 8/8: recursos permitidos/denegados, audience, firma, expiración, CA/hostname y aislamiento entre respuestas |
| Auth HTTPS real | 1/1 en la ejecución de seguridad; rechazo sin CA confiada |
| Roles PostgreSQL | 2/2: esquema nuevo y BD existente, SELECT/INSERT permitidos, DDL/UPDATE/DELETE denegados |
| Archivador con rol runtime | Nuevas observaciones persistidas con smarttraffic_archiver y Flyway/migrator separado |
| JPA/PostgreSQL antes pendientes | 2/2 con BD TLS real: conexión, idempotencia e identificación de última observación |
| Recuperación | 481 filas antes, 481 conservadas tras reiniciar brokers; 488 después, consulta OK reciente |
| Receptor de entrega local | 4/4: entrega correcta, token incorrecto, checksum incorrecto y ruta de escape |
| Carga real de imagen exportada | auth pasó sha256sum, docker load y comparación de ID tras corregir CRLF |
| Terraform | fmt/validate correctos; 5 tests de plan con proveedor simulado |
| Plan AWS autenticado | 53 creaciones, 0 cambios, 0 destrucciones; no aplicado |
| Cinco zonas | Configuración base/catálogo y env separados con overlays de roles validados |
| Sintaxis | Bash del bootstrap renderizado/importador/instalador/SQL wrapper y Python correctos |

Los 10 casos RabbitMQ/PostgreSQL antes omitidos ya se ejecutaron. No confundir
la suma de ejecuciones repetidas con cantidad de pruebas únicas. Los tests locales
no prueban red/firewall/SSM/apagado de EC2 ni alta disponibilidad.

La entrega de alertas observada es a un consumidor RabbitMQ temporal de prueba;
no existe envío por SMS/correo implementado. La prueba de schema nuevo ejecuta
los SQL V1/V2 con el rol migrator; la de JPA valida Flyway sobre la BD existente.

## Fallos y correcciones durante la preparación

1. Docker estaba instalado y detenido. Se inició su motor Linux (aproximadamente
   7.4 GiB visibles). Se mantuvieron dos contenedores ajenos; no se eliminaron
   contenedores, datos o volúmenes existentes del usuario.
2. Compose/BuildKit paralelizaba compilaciones Java aunque se limitara la
   concurrencia de Compose. El harness ahora construye servicio por servicio.
3. El primer intento con JWT de 60 s falló: el procesador dejó de avanzar y una
   consulta OK contenía estado viejo. Logs mostraron credenciales expiradas y un
   intervalo sin progreso. Después de reiniciar clientes, sin descargas iniciales,
   pasó la misma prueba de renovación/WebSocket/alerta. **No se presenta como un
   defecto de renovación del código resuelto** ni como causa de infraestructura
   demostrada. El proceso Streams puede quedar detenido ante fallo fatal; hace
   falta monitorizar frescura y recuperación operativa. No es una prueba de carga
   o suspensión del equipo ni de recuperación automática ante cualquier fallo.
4. El verificador de recuperación comparaba DateTime UTC con una conversión local.
   Se cambió a DateTimeOffset en recuperación y E2E; la repetición conservó datos
   y verificó tráfico nuevo. Test-QueryHealth rechaza explícitamente un OK viejo.
5. La carpeta de claves creada bajo la identidad aislada no permitía trabajar a
   la sesión real del usuario. Se corrigió la ACL para incluir al propietario del
   repositorio y al ejecutante específico; no se habilitó acceso general. La
   exportación real de las cinco zonas pasó después de esa corrección.
6. Se separaron roles SQL, se añadió V2 con GRANT solo SELECT/INSERT sobre la tabla
   de observaciones y overlays data/core. La clave bootstrap queda en data.
   Core aún dispone de credencial DDL de migrator para Flyway: no se presenta como
   aislamiento de privilegios frente a un contenedor core comprometido.
7. La carga real del bundle detectó CRLF en manifiestos generados en Windows:
   sha256sum trataba CR como parte del nombre. Se corrigió exportación a UTF-8/LF,
   se añadió regresión y se normalizaron los manifiestos ya exportados. Los archivos
   de password ahora tienen bytes exactos y el wrapper SQL admite finales CRLF;
   se verificó correspondencia entre secretos de data y credenciales de core.

## Artefactos preparados

- Secretos/certificados **nuevos de despliegue** en `.tools/aws-deployment-20261116`,
  ignorados por Git y protegidos con ACL. CA privada solo en OFFLINE-DO-NOT-UPLOAD;
  JWT privado en el paquete auth, claves de brokers/PG en data. No fixtures AWS.
- Configuración/env por zona, instalador con UID/permisos y overlays de roles.
- Cinco exportaciones reales de imágenes en los subdirectorios `ZONA/images`,
  con manifests de ID y SHA256. Ver [evidence/deployment-images-2026-11-16.json](evidence/deployment-images-2026-11-16.json).
- Entrega preparada mediante SSM y receptor de un solo upload en loopback,
  token y checksum. No se ha iniciado ninguna entrega a AWS.
- Plugin Session Manager **1.2.896.0 instalado**, autorizado por el propietario,
  instalador oficial con firma Amazon Valid, exit code 0; evidencia en
  [session-manager-plugin.json](evidence/session-manager-plugin.json).
  Abrir un terminal nuevo para recoger el PATH actualizado, o usar el ejecutable
  `C:\Program Files\Amazon\SessionManagerPlugin\bin\session-manager-plugin.exe`.
- Bootstrap preparado con Compose fijado, python3, firewall, apagado por arranque
  60 min y corte absoluto 23/11/2026 05:00 UTC (fin del domingo en Bogotá).
  No se ha activado esa automatización en AWS ni se crearon Scheduler/Lambda/roles extras.

Logs completos permanecen localmente en `.tools/*-current.log`,
`.tools/application-flow-retry.log` y `.tools/recovery-corrected.log`.
Resumen saneado en `evidence/release-validation-2026-11-16.json`; no incluir XML
Surefire completo, env, claves, plan o state en Git.
Los contenedores del laboratorio se detienen al finalizar las comprobaciones,
con volúmenes conservados. Docker Desktop permanece disponible y los contenedores
ajenos no se detienen como parte de ese cierre.

## Viabilidad económica y pendientes de AWS

Cuenta PAID/ACTIVE, USD 100 según API, Organizations existente. Sin cambios.
El vencimiento/cobertura/compartición del crédito sigue pendiente de comprobar
en **Billing → Credits → crédito individual**. La CLI instalada no tiene GetCredits;
la ruta y campos exactos están en [COSTS.md](COSTS.md). No se ampliaron permisos.
Las lecturas de EC2/EBS fuera de Ohio siguen denegadas por SCP: inventario global
incompleto, no ausencia demostrada de consumo ajeno.

Estimar desde 16/11 17:30 hasta finalizar el 22: **USD 42.31 base continuo**.
Incluso suponiendo toda la semana desde lunes 00:00: **USD 47.23 base**. Sesiones
de 2 h/día: USD 5.96 base incluyendo discos toda la semana. Excluye transferencia,
impuestos, servicios extra y uso ajeno. Una cuenta Paid puede facturar fuera de
los créditos; ni timer ni presupuesto son un límite duro. No se garantiza USD 0.

Pendiente tras autorización: apply, capacidad/permisos efectivos, bootstrap real,
SSM/UID/nftables/IMDS y flujos de red, entrega/carga, pruebas completas en EC2,
renovación de 900 s, recuperación/apagado reales y verificación de Billing.
Se necesita aprobación para crear/verificar alertas; no se enviaron mensajes.
Rutas sobre grafo/Observer/HA permanecen fuera de esta validación.

## Operación y autorización

- [DEPLOYMENT.md](DEPLOYMENT.md): pasos de despliegue y secretos/entrega.
- [POST-DEPLOY.md](POST-DEPLOY.md): pruebas de aceptación en AWS.
- [POWER-GUIDE.md](POWER-GUIDE.md): tutorial de apagado/encendido en consola y CLI.
- [TEARDOWN.md](TEARDOWN.md): eliminación definitiva y discos retenidos, con advertencias.
- [COSTS.md](COSTS.md): costos, Billing, cierre del domingo y recursos residuales.

Antes de apply, confirmar vigencia/cobertura del crédito y aprobar explícitamente
**el plan final**. Propuesta económica para revisar: sesiones iniciales de 60 min,
sin exceder USD 60 de créditos durante la semana, corte al finalizar el domingo,
reserva frente al saldo inicial de USD 100. Ese objetivo no es una barrera técnica
contra sobrecostos o créditos compartidos. Operación continua exige cambiar el
límite de 60 minutos mediante un nuevo plan revisado; el plan actual no es 24/7.
La aprobación de creación no autoriza destroy ni borrar EBS/snapshots/datos.
