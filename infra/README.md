# Infraestructura y Zero Trust

## Zonas

- `vm-auth`: única zona autorizada para emitir JWT.
- `vm-data`: Kafka KRaft de un nodo, RabbitMQ y PostgreSQL.
- `vm-ingest`: simulador y productor de tráfico.
- `vm-core`: procesamiento estatal y archivado.
- `vm-app`: dashboard, alertas y consultas.

## Política de red

Cada VM debe aplicar `policy drop` en entrada y salida. Solo se permiten conexiones explícitas entre IPs y puertos autorizados. El tráfico de retorno debe aceptarse únicamente como `ct state established,related`.

## Puertos del laboratorio seguro

- Auth HTTPS: `8443`, desde ingest/core/app y data para JWKS.
- Kafka SASL_SSL/OAUTHBEARER: `9093`, desde ingest/core.
- RabbitMQ TLS/OAuth2: `5671`, desde core/app.
- PostgreSQL TLS: `5432`, desde core, con `sslmode=verify-full`.
- Kafka interno/controlador: `9092`/`9094`, solo loopback con TLS mutuo.

La matriz de IPs privadas y reglas está en [aws](aws/README.md).
Los puertos de desarrollo sin TLS no se deben trasladar a AWS.

## Perfil seguro

El perfil seguro requiere TLS verificado para Auth Server, Kafka y RabbitMQ.
Los endpoints, certificados y permisos se entregan mediante montajes individuales; no
se escriben en `docker-compose.yml` ni en el repositorio. La plantilla de
variables está en `infra/secure.env.example` y el procedimiento de certificados
de desarrollo en `infra/tls/README.md`.

No se ha provisionado un gestor de secretos de pago. La política Zero Trust mantiene el acceso mínimo: Kafka sólo acepta clientes
de `vm-ingest` y `vm-core`, RabbitMQ sólo de `vm-core` y `vm-app`, PostgreSQL
sólo de `vm-core`, y Auth Server de los clientes de servicio y data para JWKS.

En `vm-app`, `alert-dispatcher` y `query-client` son procesos separados. El
perfil seguro usa `RABBITMQ_HOST`, `RABBITMQ_PORT=5671`, `RABBITMQ_VHOST` y
`AUTH_TOKEN_ENDPOINT` como nombres canónicos; los secretos y las claves RS256
se inyectan mediante archivos controlados por servicio. El broker debe tener habilitado
OAuth2/JWT y permisos mínimos por vhost, exchange y cola.
