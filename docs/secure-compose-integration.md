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

## RabbitMQ OAuth2 y topología

El broker seguro debe escuchar AMQPS en `5671`, tener habilitado el plugin OAuth2/JWT del proveedor y validar el `aud`/`scope` del token. Las identidades no se crean como usuarios `guest`: RabbitMQ recibe el JWT como contraseña SASL mediante `RabbitJwtCredentialsProvider`.

La topología declarada por los servicios es:

| Exchange | Tipo | Colas/routing keys |
|---|---|---|
| `traffic_updates` | fanout | `traffic.alerts.dispatcher.v1`, dashboard y query state |
| `traffic_alerts` | fanout | consumidores de alertas |
| `query_traffic` | direct | `query_traffic_queue` con `query-client.query` |
| `query_answers` | topic | cola exclusiva `query_answers.query-client.<UUID>` por instancia; routing key igual al nombre de cola |

El vhost y los permisos deben limitar cada identidad a los exchanges y colas que utiliza. El repositorio declara la topología y valida TLS; el plugin OAuth2, los permisos efectivos y la política de usuarios deben ser configurados por infraestructura antes de una prueba cloud.

## JWT seguro

El perfil local conserva HMAC para no exigir certificados al desarrollador. El perfil seguro usa `RS256`: `SECURITY_JWT_PRIVATE_KEY` sólo existe en el Auth Server y `SECURITY_JWT_PUBLIC_KEY` se monta en los consumidores. Auth Server publica `/.well-known/jwks.json`; ningún token, secreto o clave privada se registra o se commitea.

El dashboard usa `traffic-dashboard` y `AUTH_CLIENT_TRAFFIC_DASHBOARD_SECRET`.
Dashboard, consultas y alertas cargan `AUTH_CA_CERTIFICATE` como CA del endpoint
HTTPS; el truststore de RabbitMQ se configura por separado. Nunca se desactiva
la verificación TLS para aceptar una CA privada.

`query_answers` cambió de direct a topic. RabbitMQ no permite cambiar el tipo de
un exchange existente: usar un vhost nuevo durante la migración o realizar un
reemplazo coordinado después de drenar los mensajes. No borrar automáticamente
el exchange ni las colas del entorno anterior.

La prueba reproducible de TLS/OAuth2 y permisos está en
`infra/local-security/Test-Security.ps1`. Las pruebas locales no sustituyen la
verificación de Kafka y el flujo completo en las cinco zonas.
