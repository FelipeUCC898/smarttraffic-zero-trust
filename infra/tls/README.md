# TLS local y cloud

Este directorio contiene únicamente instrucciones y plantillas. Los certificados,
claves privadas, truststores y contraseñas quedan fuera de Git; `.gitignore`
ignora `*.pem`, `*.key` y los artefactos generados.

## Certificados de desarrollo

Ejecuta `generate-dev-certs.ps1` sólo en una máquina de desarrollo con OpenSSL.
El script crea una CA local y certificados separados para `auth-server`,
`kafka` y `rabbitmq`, incluyendo SAN para los nombres usados dentro de Docker.
No uses estos certificados en cloud ni en la entrega final.

## Requisitos para cloud

Antes de activar el perfil seguro, A debe recibir del proveedor:

1. FQDN y puerto TLS de Kafka, mecanismo OAuth/OIDC y permisos de los clientes.
2. FQDN y puerto TLS de RabbitMQ, CA confiable, vhost y permisos de publicación.
3. FQDN HTTPS del Auth Server y certificado cuyo SAN coincida con ese FQDN.
4. Ubicación del secret manager y procedimiento de rotación.

Los endpoints se documentan en `infra/secure.env.example`; nunca se sustituyen
por valores ficticios en el Compose de producción. Antes de arrancar, ejecuta
`infra/validate-secure-config.ps1`. El script valida formato y archivos locales,
pero no puede verificar el registro remoto de clientes ni permisos del broker.
