# Integración Docker segura

El Compose local mantiene Kafka en `PLAINTEXT` y RabbitMQ con `guest` para pruebas de infraestructura. El simulador y el procesador no deben conectarse a ese perfil: ambos servicios exigen Kafka `SASL_SSL/OAUTHBEARER`, RabbitMQ TLS verificado y Auth Server por HTTPS.

## Credenciales

El Auth Server valida dos clientes independientes:

| Servicio | `AUTH_CLIENT_ID` | Secreto inyectado |
|---|---|---|
| Simulador | `traffic-simulator` | `AUTH_CLIENT_TRAFFIC_SIMULATOR_SECRET` |
| Procesador | `processor` | `AUTH_CLIENT_PROCESSOR_SECRET` |

Los secretos sólo deben existir en `.env` local o en el gestor de secretos del proveedor cloud. No se deben escribir en `docker-compose.yml`, commits, logs ni Pull Requests.

## Arranque del perfil seguro

Completar `.env` a partir de `.env.example`, colocar los certificados/truststores en un directorio fuera del repositorio y verificar que los endpoints pertenezcan al entorno TLS real. Después:

```powershell
docker compose --profile secure-integration build traffic-sensor-simulator density-processor
docker compose --profile secure-integration up -d traffic-sensor-simulator density-processor
docker compose --profile secure-integration ps
docker compose --profile secure-integration logs --tail=100 traffic-sensor-simulator density-processor
```

El perfil no se inicia con `docker compose up` normal, porque no sería correcto apuntar servicios que requieren TLS a los brokers locales PLAINTEXT. Antes de ejecutar, confirmar: `SECURE_AUTH_TOKEN_ENDPOINT` por HTTPS, Kafka en 9093, RabbitMQ en 5671, CA/truststores válidos y permisos de los clientes.
