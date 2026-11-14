# SmartTraffic

Proyecto final de POO, Sistemas Distribuidos y Sistemas Operativos.

## Equipo

- Integrante A: infraestructura, Docker, VMs, nftables y autenticación.
- Integrante B: microservicios Java/Spring Boot, dominio, Kafka y RabbitMQ.
- Integrante C: PostgreSQL, JPA, dashboard, WebSocket y pruebas.

## Flujo de trabajo

1. `main` es la rama de producción y permanece protegida.
2. `develop` es la rama de integración del equipo.
3. Cada integrante trabaja en ramas propias, por ejemplo `dev-uno/feat-auth-server`, `dev-dos/feat-traffic-processor` y `dev-tres/feat-persistence-tests`.
4. Cada rama personal se actualiza desde `develop` y se integra a `develop` mediante Pull Request y revisión cruzada.
5. Cuando `develop` está estable, se abre un Pull Request de `develop` hacia `main` para la entrega.
6. Los commits deben ser pequeños y describir una sola intención.

## Convención de commits

Usar prefijos `feat`, `fix`, `test`, `docs` o `chore`.

Ejemplo: `feat(auth): emitir JWT con roles`.

## Arquitectura prevista

El sistema se distribuye en `vm-auth`, `vm-data`, `vm-ingest`, `vm-core` y `vm-app`, con autenticación servicio-a-servicio mediante JWT, Kafka para ingesta y RabbitMQ para distribución de estado.

## Arranque integrado local

El Compose también construye y conecta los módulos de archivado y dashboard
con la infraestructura compartida. El inicializador `kafka-init` crea de forma
idempotente el tópico `raw_traffic_data` antes de arrancar el consumidor.

```powershell
docker compose up -d
docker compose ps
```

Los servicios de aplicación quedan disponibles en `127.0.0.1:8080` para auth y
`127.0.0.1:8081` para el dashboard. Kafka, PostgreSQL y RabbitMQ sólo se
publican en loopback. Para apagar el entorno:

```powershell
docker compose down
```
