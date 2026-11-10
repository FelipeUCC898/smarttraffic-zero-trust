# Auth Server

Servicio de autoridad para SmartTraffic. Expone `POST /token` y será el único componente autorizado para emitir JWT firmados.

## Estado actual

El endpoint `POST /token` valida las credenciales configuradas y emite un JWT firmado con HMAC. La clave se proporciona mediante la variable `JWT_SECRET`; nunca debe almacenarse en el repositorio.

Credenciales de desarrollo: `traffic-simulator` / `demo-secret`.

La prueba unitaria de `JwtService` verifica la emisión y firma del token.

## Ejecución

```bash
mvn spring-boot:run
```
