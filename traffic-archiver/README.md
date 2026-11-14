# Persistencia y dashboard de SmartTraffic

Los módulos de C usan el PostgreSQL 16 y RabbitMQ de `docker-compose.yml`.
El archivador consume raw_traffic_data de Kafka y guarda observaciones en PostgreSQL. El dashboard consume traffic_updates de RabbitMQ y muestra estados por WebSocket. Los contratos están descritos en docs/traffic-contracts.md.

## Arranque local

Desde la raíz, prepara `.env` a partir de `.env.example` si todavía no existe.
Configura los valores locales de PostgreSQL, `JWT_SECRET` y
`RABBITMQ_PASSWORD` con la contraseña del usuario del broker de desarrollo.
Usa líneas `CLAVE=valor` para que Docker Compose y Spring puedan leer el mismo
archivo. Ambos módulos deben arrancar desde la raíz para leerlo.

```powershell
docker compose up -d
docker compose exec postgres-db pg_isready
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
docker compose ps
```

Abre http://127.0.0.1:8081. Cuando ambos módulos estén en ejecución, publica en
el exchange fanout `traffic_updates` sin routing key. Puedes
usar la consola de RabbitMQ en http://127.0.0.1:15672. Ejemplo de cuerpo JSON.

```json
{
  "zoneId": "north",
  "measuredAt": "2026-11-10T15:00:00Z",
  "density": 0.6,
  "congestionState": "MODERADA", "version": "v1"
}
```

El dashboard debe mostrar el estado de zona. Para probar el archivado, publica una observación en raw_traffic_data con clave sensorId y los campos descritos en docs/traffic-contracts.md. Consulta traffic_observation en PostgreSQL. Republicar el mismo eventId debe conservar una sola fila. El dashboard conserva en memoria el último
estado recibido por zona mientras su proceso está activo.

## Pruebas

Los tests unitarios se ejecutan con `mvn test` en cada módulo. Para probar la
migración y las consultas con PostgreSQL, arranca `postgres-db` y ejecuta
desde la raíz.

```powershell
$env:SMARTTRAFFIC_POSTGRES_TEST = 'true'
mvn -f traffic-archiver/pom.xml "-Dspring.config.additional-location=file:../.env[.properties]" -Dtest=PostgreSqlConnectionTest test
Remove-Item Env:SMARTTRAFFIC_POSTGRES_TEST
```

Cuando B conecte la topología Kafka con el publicador RabbitMQ, repite la prueba de punta a punta en
`develop` con un evento real y valida los permisos del broker con A.

En el arranque integrado de Compose no es necesario crear manualmente el
tópico: `kafka-init` garantiza la existencia de `raw_traffic_data` antes de
iniciar este consumidor.

## Conexión a Kafka

El broker de Felipe anuncia kafka:9092 para clientes dentro de Docker.
Para ejecutar el archivador desde el host, KAFKA_BOOTSTRAP_SERVERS y los
advertised listeners deben ser accesibles desde ese proceso. Coordina con A
un listener para host o ejecuta el módulo dentro de la red data-net.

El tópico raw_traffic_data debe existir antes de iniciar los consumidores.
El grupo del archivador es traffic-archiver-v1 y debe ser distinto al del
procesador de densidad. Los offsets se confirman después del guardado.
Los tests usan Java 17. La ejecución verificada con Java 25 necesitó el agente
Byte Buddy y net.bytebuddy.experimental=true.

En el arranque integrado de Compose no es necesario crear manualmente el
tópico: `kafka-init` garantiza la existencia de `raw_traffic_data` antes de
iniciar este consumidor.

## Recuperación y orden de mensajes

- Archivador: publica una observación y confirma que PostgreSQL guarda una fila.
  Reinicia el archivador, espera a que Kafka le asigne la partición, reenvía el
  mismo evento y envía otro con un eventId nuevo. Ambos conteos deben dar 1.
- Dashboard: publica tres estados para una zona nueva en orden de envío 10:00,
  10:02 y 10:01, con densidades 10, 30 y 20. Debe conservar la densidad 30.
  Reinicia el dashboard y publica un estado de una zona nueva cuando vuelva
  a conectarse. La fila debe aparecer sin recargar el navegador.

Ambas comprobaciones pasaron manualmente con los servicios locales.
