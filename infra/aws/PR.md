# Título propuesto

feat(aws): preparar laboratorio de cinco VMs con entrega segura y cierre programado

# Base

`develop` ← `dev-codex/feat-aws-readiness`

# Descripción

La preparación AWS carecía de Compose instalado, entrega reproducible de imágenes
y un procedimiento de cierre que distinguiera Stop de los discos retenidos.
El bootstrap instala Compose con SHA256 fijado y Python, exige firewall activo,
prepara apagado por boot y cutoff absoluto al finalizar el domingo 22/11/2026.
El helper solo admite preparación, deja EC2 deshabilitado por defecto y exige
cuenta/identidad no-root. No se ejecuta apply ni se modifica AWS.

Se preparan certificados y env locales filtrados por VM, manifests de imágenes,
entrega mediante túnel SSM con token/checksum y rechazo de rutas de archivo inseguras.
Overlays de PostgreSQL separan bootstrap, migración y runtime; una migración concede
SELECT/INSERT sobre la tabla de observaciones. Core conserva migrator para Flyway,
por lo que no se afirma aislamiento de DDL frente a un contenedor comprometido.

Los probes exigen filas/estado recientes, WebSocket y una alerta realmente recibida,
renovación corta y recuperación con volúmenes. DateTimeOffset corrige comparación
UTC/local en verificación; builds por servicio evitan concurrencia Java implícita.
Se añaden tutoriales de despliegue, parada/arranque, eliminación y revisión de costos.

# Validación

- Terraform fmt/validate, 5 planes simulados y plan autenticado 53/0/0 sin aplicar.
- Cinco configuraciones Compose/env y roles overlays correctos.
- Flujo completo: 18 observaciones nuevas y consulta reciente OK.
- 10 tests reales Kafka/PG, 8 RabbitMQ y 2 JPA/PG correctos; HTTPS real correcto.
- Probes de WebSocket/alerta/renovación y dos de roles SQL correctos.
- Recuperación: 481 filas conservadas, 488 después y estado nuevo OK.
- Cuatro tests del receptor, sintaxis Python/Bash/PowerShell y diff check correctos.

# Límites para revisión

AWS no desplegado. Cuenta PAID/ACTIVE con USD 100 según API y organización existente;
vencimiento/cobertura de créditos requieren revisión en Billing. SCP impide inventario
fuera de Ohio. Semana completa: USD 47.23 base, sin transferencia/impuestos/usos ajenos;
no es una garantía de factura cero. Bootstrap/SSM/firewall/timer/renovación prolongada
y aceptación de aplicaciones en EC2 quedan para una ventana expresamente autorizada.
El primer ensayo con JWT de 60 s perdió progreso; pasó tras reiniciar sin descargas.
No se afirma causa establecida ni recuperación automática de cualquier fallo fatal.

Evidencia y operación: [RELEASE-READINESS.md](RELEASE-READINESS.md).
Este archivo prepara una descripción; no representa una PR remota abierta.
