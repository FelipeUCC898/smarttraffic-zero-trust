# Auth Server

Servicio de autoridad para SmartTraffic. Expone `POST /token` y será el único componente autorizado para emitir JWT firmados.

## Estado actual

El endpoint `POST /token` valida las credenciales configuradas y emite un JWT firmado con HMAC. La clave se proporciona mediante la variable `JWT_SECRET`; nunca debe almacenarse en el repositorio.

Los clientes permitidos y sus secretos se configuran mediante
`AUTH_CLIENT_TRAFFIC_SIMULATOR_SECRET` y `AUTH_CLIENT_PROCESSOR_SECRET`.
El servidor rechaza clientes desconocidos, secretos vacíos o valores
incorrectos con `401 Unauthorized`; los secretos nunca deben almacenarse en el
repositorio.

La prueba unitaria de `JwtService` verifica la emisión y firma del token.

## Ejecución

```bash
mvn spring-boot:run
```
