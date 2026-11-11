# Contratos de SmartTraffic

Referencia: sección 8 del tablero de Miro, leída el 10 de noviembre de 2026.
Los mensajes son JSON UTF-8 con identificadores inmutables y fechas ISO-8601 UTC.

## Observaciones crudas

Kafka: tópico `raw_traffic_data`, clave `sensorId`.
Productor: traffic_sensor_simulator. Consumidores independientes: archiver y
density_processor, cada uno con su propio grupo Kafka.

Campos obligatorios: `eventId` UUID, `sensorId`, `zoneId`, `measuredAt`,
`vehicleCount` entero no negativo, `averageSpeedKmh` decimal no negativo y
`congestionState` (DESPEJADA, MODERADA o CONGESTIONADA).

C conserva una fila por eventId en traffic_observation. Los identificadores de
sensor y zona admiten hasta 128 caracteres, decisión local pendiente de revisión
con B. Un fallo de almacenamiento mantiene el mensaje pendiente y se reintenta
cada cinco segundos. Un mensaje inválido se registra como error y se descarta
por el manejador Kafka, antes de continuar con el siguiente registro.

## Estados derivados

RabbitMQ: exchange `traffic_updates`, tipo `fanout`. Productor:
density_processor. Consumidores: alert_dispatcher y dashboard, en colas distintas.
No hay routing key.

Campos: `zoneId`, `measuredAt`, `density`, `congestionState` y `version`.
El dashboard interpreta density como decimal no negativo y conserva el último
estado por fecha. La versión se transporta como texto y su semántica debe
confirmarse con B. El dashboard mantiene el estado en memoria durante su ejecución.

## Consultas y respuestas

Exchange `query_traffic` de tipo direct/topic, cola `query_traffic_queue`,
routing key `clientId.query`. Productor query_client, consumidor alert_dispatcher.
Campos: requestId UUID, clientId, originZone, destinationZone, requestedAt UTC.

Exchange `query_answers`, routing key `clientId.answer`. Productor
alert_dispatcher, consumidor query_client. Campos: requestId, clientId, path[],
estimatedMinutes, generatedAt UTC y status (OK, NOT_FOUND, ERROR).
El servicio alert_dispatcher y el cliente de consultas siguen pendientes de
coordinación con B.

## Integración y seguridad

La infraestructura local actual publica Kafka con PLAINTEXT. El tablero exige
JWT para los brokers; A y B deben acordar y habilitar los mecanismos y las
credenciales antes de validar esa integración. El dashboard local escucha
en 127.0.0.1 y mantiene el origen WebSocket restringido al mismo origen.

Usar .env local para secretos. Cada cambio se revisa mediante PR a develop.
Los cambios incompatibles requieren una versión nueva acordada por el equipo.
