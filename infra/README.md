# Infraestructura y Zero Trust

## Zonas

- `vm-auth`: única zona autorizada para emitir JWT.
- `vm-data`: Kafka, ZooKeeper, RabbitMQ y PostgreSQL.
- `vm-ingest`: simulador y productor de tráfico.
- `vm-core`: procesamiento estatal y archivado.
- `vm-app`: dashboard, alertas y consultas.

## Política de red

Cada VM debe aplicar `policy drop` en entrada y salida. Solo se permiten conexiones explícitas entre IPs y puertos autorizados. El tráfico de retorno debe aceptarse únicamente como `ct state established,related`.

## Puertos iniciales

- Auth Server: `8080`, solo desde `vm-ingest`, `vm-core` y `vm-app`.
- Kafka: `9092`, solo desde `vm-ingest` y `vm-core`.
- RabbitMQ: `5672`, solo desde `vm-core` y `vm-app`.
- PostgreSQL: `5432`, solo desde `vm-core`.

Las IPs definitivas de las VMs deben completarse antes de aplicar reglas en producción.

## Perfil seguro

El perfil seguro requiere TLS verificado para Auth Server, Kafka y RabbitMQ.
Los endpoints, certificados y permisos se inyectan desde un secret manager; no
se escriben en `docker-compose.yml` ni en el repositorio. La plantilla de
variables está en `infra/secure.env.example` y el procedimiento de certificados
de desarrollo en `infra/tls/README.md`.

La política Zero Trust mantiene el acceso mínimo: Kafka sólo acepta clientes
de `vm-ingest` y `vm-core`, RabbitMQ sólo de `vm-core` y `vm-app`, PostgreSQL
sólo de `vm-core`, y Auth Server sólo de los clientes de servicio autorizados.
