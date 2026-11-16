# Integración Docker segura

El Compose local mantiene Kafka en `PLAINTEXT` y RabbitMQ con `guest` para pruebas de infraestructura. Los servicios seguros no deben conectarse a ese perfil: el simulador y el procesador exigen Kafka `SASL_SSL/OAUTHBEARER`, RabbitMQ TLS verificado y Auth Server por HTTPS. El archivador y el dashboard se exponen como servicios separados (`traffic-archiver-secure` y `traffic-dashboard-secure`) para no romper las pruebas locales.

## Credenciales

El Auth Server valida tres clientes independientes:

| Servicio | `AUTH_CLIENT_ID` | Secreto inyectado |
|---|---|---|
| Simulador | `traffic-simulator` | `AUTH_CLIENT_TRAFFIC_SIMULATOR_SECRET` |
| Procesador | `processor` | `AUTH_CLIENT_PROCESSOR_SECRET` |
| Archivador | `traffic-archiver` | `AUTH_CLIENT_TRAFFIC_ARCHIVER_SECRET` |
| Alertas | `alert-dispatcher` | `AUTH_CLIENT_ALERT_DISPATCHER_SECRET` |
| Consultas | `query-client` | `AUTH_CLIENT_QUERY_CLIENT_SECRET` |

Los secretos sólo deben existir en `.env` local o en el gestor de secretos del proveedor cloud. No se deben escribir en `docker-compose.yml`, commits, logs ni Pull Requests.

El mismo secreto de `traffic-archiver` debe estar registrado en el Auth Server seguro y en el `.env` del entorno que ejecuta el archivador. Si ambos valores no coinciden, el Auth Server rechazará el token.

## Arranque del perfil seguro

Completar `.env` a partir de `.env.example`, colocar los certificados/truststores en un directorio fuera del repositorio y verificar que los endpoints pertenezcan al entorno TLS real. Después:

Antes de arrancar, ejecutar el preflight sin revelar secretos en la consola:

```powershell
.\infra\validate-secure-config.ps1
```

El preflight comprueba que el endpoint sea HTTPS y termine en `/token`, que Kafka tenga formato `host:puerto`, que existan la CA y el truststore dentro de `SECURE_CERTS_DIR` y que Git ignore `.env`. No puede comprobar desde el repositorio que el cliente `traffic-archiver` esté registrado en el Auth Server ni que el broker haya concedido permisos; esas dos verificaciones deben hacerse en el proveedor.

```powershell
docker compose --profile secure-integration build traffic-sensor-simulator density-processor
docker compose --profile secure-integration up -d traffic-sensor-simulator density-processor traffic-archiver-secure traffic-dashboard-secure alert-dispatcher-secure query-client-secure
docker compose --profile secure-integration ps
docker compose --profile secure-integration logs --tail=100 traffic-sensor-simulator density-processor
```

El perfil no se inicia con `docker compose up` normal, porque no sería correcto apuntar servicios que requieren TLS a los brokers locales PLAINTEXT. Antes de ejecutar, confirmar: `SECURE_AUTH_TOKEN_ENDPOINT` por HTTPS, Kafka en 9093, RabbitMQ en 5671, CA/truststores válidos y permisos de los clientes.

El archivador obtiene su token OAuthBearer mediante el endpoint HTTPS configurado y carga la CA/truststore declarada para no depender de la confianza global de la JVM. Consume Kafka mediante SASL_SSL/OAUTHBEARER con una credencial propia. La prueba completa requiere que el proveedor haya registrado esa identidad, que el secreto coincida y que Kafka autorice al archivador en el tópico `raw_traffic_data` y en el grupo `traffic-archiver-v1`. La credencial del dashboard también debe configurarse antes de ejecutar la prueba completa.
