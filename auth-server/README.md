# Auth Server

Servicio de autoridad para SmartTraffic. Expone `POST /token` y será el único componente autorizado para emitir JWT firmados.

## Estado actual

El endpoint valida que `clientId` y `clientSecret` no estén vacíos, pero responde `501 Not Implemented` hasta integrar la firma JWT y la validación de clientes.

## Ejecución

```bash
mvn spring-boot:run
```
