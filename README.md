# SmartTraffic

Proyecto final de POO, Sistemas Distribuidos y Sistemas Operativos.

## Equipo

- Integrante A: infraestructura, Docker, VMs, nftables y autenticación.
- Integrante B: microservicios Java/Spring Boot, dominio, Kafka y RabbitMQ.
- Integrante C: PostgreSQL, JPA, dashboard, WebSocket y pruebas.

## Flujo de trabajo

1. `main` permanece protegida y contiene únicamente versiones revisadas.
2. Cada tarea se desarrolla en una rama `feature/<responsable>-<tarea>`.
3. Los cambios se integran mediante Pull Request y revisión cruzada.
4. Los commits deben ser pequeños y describir una sola intención.

## Convención de commits

Usar prefijos `feat`, `fix`, `test`, `docs` o `chore`.

Ejemplo: `feat(auth): emitir JWT con roles`.

## Arquitectura prevista

El sistema se distribuye en `vm-auth`, `vm-data`, `vm-ingest`, `vm-core` y `vm-app`, con autenticación servicio-a-servicio mediante JWT, Kafka para ingesta y RabbitMQ para distribución de estado.
