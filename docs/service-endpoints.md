# Endpoints y configuración de integración

Este documento entrega a los integrantes B y C los puntos de conexión que
deben consumir los servicios. No contiene secretos, contraseñas ni claves
privadas.

## Perfil local con Docker Compose

Dentro de la red `data-net`, los servicios deben usar los nombres DNS de
Compose, no `localhost`:

| Servicio | Endpoint dentro de Docker | Uso |
|---|---|---|
| Auth Server | `http://auth-server:8080/token` | Solicitar JWT de servicio |
| Kafka | `kafka:9092` | Publicar/consumir `raw_traffic_data` |
| RabbitMQ AMQP | `rabbitmq:5672` | Publicar/consumir `traffic_updates` |
| PostgreSQL | `postgres-db:5432` | Persistencia del archivador |

Desde la máquina host, los mismos servicios se exponen sólo por loopback:

| Servicio | Endpoint host |
|---|---|
| Auth Server | `http://127.0.0.1:8080/token` |
| Kafka | `127.0.0.1:9092` |
| RabbitMQ AMQP | `127.0.0.1:5672` |
| RabbitMQ Management | `http://127.0.0.1:15672` |
| Dashboard | `http://127.0.0.1:8081` |
| Query Client | `http://127.0.0.1:8092/api/traffic/{zoneId}` |

## Contratos de mensajería

- Kafka topic: `raw_traffic_data`.
- Clave Kafka: `sensorId`.
- RabbitMQ exchange: `traffic_updates`.
- Tipo de exchange: `fanout`.
- El dashboard escucha estados por WebSocket en `/ws/zones`.
- `query-client` mantiene una vista de consulta por zona en su propia cola RabbitMQ.
- `alert-dispatcher` consume `traffic_updates` y publica alertas de congestión en `traffic_alerts`.
- `query-client` publica consultas en `query_traffic` y espera respuestas correlacionadas en `query_answers`; vence con HTTP 504 después de dos segundos.
- La especificación completa está en `docs/traffic-contracts.md`.

## Variables que deben consumir los microservicios

```env
AUTH_TOKEN_ENDPOINT=http://auth-server:8080/token
AUTH_CLIENT_ID=processor
AUTH_CLIENT_SECRET=REPLACE_IN_LOCAL_ENV_ONLY
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_VHOST=/
```

El simulador debe usar su propio `AUTH_CLIENT_ID` y secreto; no debe reutilizar
el secreto del procesador.

## Perfil cloud seguro

Los valores reales de cloud todavía deben ser entregados por infraestructura.
Cuando existan, se sustituyen mediante variables sin cambiar el código:

```env
AUTH_TOKEN_ENDPOINT=https://AUTH_FQDN:AUTH_TLS_PORT/token
KAFKA_BOOTSTRAP_SERVERS=KAFKA_FQDN:KAFKA_TLS_PORT
RABBITMQ_HOST=RABBITMQ_FQDN
RABBITMQ_PORT=5671
RABBITMQ_VHOST=/smarttraffic
```

En cloud, Kafka debe usar `SASL_SSL` con `OAUTHBEARER` y RabbitMQ debe usar
TLS con validación de hostname y JWT como credencial de servicio. Los
truststores se montan desde el secret manager; nunca se suben al repositorio.
Los clientes OAuth2 independientes son `traffic-simulator`, `processor`,
`traffic-archiver`, `alert-dispatcher`, `query-client` y `traffic-dashboard`. Las credenciales se
inyectan con `AUTH_CLIENT_*_SECRET`; no se guardan en Compose ni en Git.

## Criterio de integración

Las consultas de una zona devuelven `status`, `state`, `path` y `requestId` basados
en el último evento recibido. Una zona aún no observada devuelve `NOT_FOUND`.
No se informa una ETA ficticia: el cálculo de trayectos entre zonas requiere
incorporar un mapa vial. El dispatcher responde `ROUTE_UNAVAILABLE` cuando recibe
una solicitud entre zonas distintas.

1. B configura sus clientes usando las variables anteriores.
2. Se solicita un JWT válido al Auth Server.
3. Se publica una observación en `raw_traffic_data`.
4. El archivador guarda la observación y el procesador publica en
   `traffic_updates`.
5. C confirma que el dashboard refleja el estado por WebSocket.
