# Comprobación posterior al despliegue autorizado

Este es un protocolo preparado, no evidencia de EC2. Registrar fecha/IDs/digests
por prueba, valores esperados, observados y errores. Guardar evidencia saneada;
nunca tokens, contraseñas, claves, env completos o certificados privados.

## Plataforma y controles

| Comprobación | Evidencia exigida |
|---|---|
| SSM | Las cinco instancias aparecen Online y permiten sesión temporal |
| Bootstrap | cloud-init terminó sin error; Compose 5.1.3 instalado y checksum verificado |
| Identidad de imagen | IDs cargados coinciden con manifiesto por VM; no builds ni pulls implícitos |
| Aislamiento | Solo los servicios de la tabla de DEPLOYMENT.md en su host; no Compose general |
| SG | Ninguna entrada CIDR pública; referencias entre grupos solo en puertos TLS aprobados |
| Host | INPUT/OUTPUT/FORWARD DROP y reglas por IP/puerto; Docker depende del firewall |
| IMDS | IMDSv2 obligatorio; acceso no-root denegado por nftables |
| Secretos | CA privada ausente en todas las VMs; JWT privado solo auth; claves de brokers solo data |
| Apagado | Timer por boot y calendario del domingo; shutdown EC2=stop; prueba real autorizada |

En cada VM vía SSM, sin imprimir archivos de secretos:

```bash
sudo cloud-init status --long
sudo systemctl --no-pager status smarttraffic-firewall docker amazon-ssm-agent
sudo systemctl list-timers 'smarttraffic-lab-*'
sudo systemctl cat smarttraffic-lab-deadline.timer smarttraffic-deadline-check.service
sudo nft list ruleset
docker compose version
df -h / /var/lib/docker
```

Usar sudo/root para Docker si el usuario SSM no pertenece al grupo. No añadir
grupos de acceso administrativo indiscriminadamente. Host root permanece una
frontera de confianza; no equivale a aislamiento frente a root comprometido.

## Red y TLS

Desde ingest/core/app: Auth HTTPS 10.42.10.10:8443 positivo con CA y hostname/IP.
Desde data: JWKS positivo. Desde ingest/core: Kafka 9093 positivo. Core/app →
RabbitMQ 5671 positivo. Solo core → PostgreSQL 5432 positivo. Para un negativo,
comprobar simultáneamente que el servicio está sano desde un origen autorizado:
un timeout aislado no demuestra firewall. Ingest → PostgreSQL y app → Kafka deben
ser denegados. Kafka 9092/9094 solo loopback y mTLS, no accesibles desde otras VMs.

Comprobar que el dashboard/query solo escucha en loopback y se accede mediante
SSM. Desde fuera, probar puertos públicos cerrados mientras las rutas privadas
funcionan. Conservar SG/nftables/resultado para diferenciar firewall de servicio caído.

Ejemplos desde un host autorizado, con rutas de CA correctas:

```bash
curl --fail --cacert /opt/smarttraffic/secrets/ca.pem https://10.42.10.10:8443/.well-known/jwks.json
openssl s_client -connect 10.42.10.11:5671 -CAfile /opt/smarttraffic/secrets/ca.pem -verify_ip 10.42.10.11 -verify_return_error </dev/null
```

Repetir con CA equivocada y nombre/IP no incluido en SAN: deben fallar por
validación, no por ausencia de conexión. No usar `-k`, verify=false ni sslmode=require
como sustitutos de verify-full. El listener TLS de RabbitMQ usa JWT de cliente;
no se presenta como autenticación mutua TLS.

## Flujo funcional y pruebas de seguridad

1. Anotar T0 UTC y conteo inicial de `traffic_observation`; revisar offsets Kafka.
2. Arrancar ingest y verificar eventos **nuevos** de secure-e2e-sensor/north/42,
   con measured_at posterior a T0; no contar datos anteriores o security-probe.
3. Confirmar filas nuevas archivadas con TLS. En la sesión BD, consultar pg_stat_ssl
   y rol runtime; rechazar DDL y no conceder superuser/CREATE por resolver un error.
4. Por túnel SSM en vm-app, consultar `http://127.0.0.1:8092/api/traffic/north`.
   Exigir status OK, requestId UUID, estado posterior a T0 y una respuesta reciente.
   Dos consultas deben tener IDs diferentes; probar dos réplicas para aislamiento
   de reply queues y correlation ID. Un OK con fecha vieja **no aprueba** la prueba.
5. Dashboard: abrir página 8081 y WebSocket `/ws/zones`; capturar un estado reciente
   recibido por el socket, no solo el HTML o un handshake 101.
6. Alerta: observar un consumidor temporal autorizado, conectado antes de una
   transición DESPEJADA → CONGESTIONADA. Exigir mensaje CONGESTION con zona y fecha
   recientes y verificar que repetir el estado no genera alertas duplicadas.
   No hay canal de SMS/correo implementado: no afirmar entrega por esos medios.
7. Autenticación: obtención HTTPS con CA confiada; contraseña incorrecta rechazada;
   JWT expirado/firma/issuer/audience incorrectos rechazados; identidades sin ACL
   no publican/leen recursos ajenos. No distribuir la clave JWT privada a la VM
   probadora para fabricar estos casos: preparar tokens negativos fuera de AWS,
   bajo administración autorizada, y transferir solo esos tokens de corta vida.
8. TLS PostgreSQL: sslmode=disable debe rechazarse; CA/nombre incorrectos deben
   fallar por validación. No basta autenticar con usuario bootstrap.
9. Renovación: mantener tráfico más de una vida JWT real (900 s por defecto en
   despliegue), y exigir filas y estado posteriores al vencimiento inicial. Las
   pruebas locales usan 60 s para acelerar; no equivalen a una prueba larga en EC2.
10. Recuperación: registrar conteo/IDs de datos; detener solo el laboratorio,
    reiniciar data y clientes en orden con los mismos volúmenes. Exigir conservación
    de datos previos y datos nuevos procesados después del reinicio. No limpiar
    changelog/offsets, reinicializar passwords ni usar down -v como reparación.
11. Apagado: probar un intervalo corto autorizado y confirmar Stopped en AWS;
    después revalidar el timer absoluto del domingo. El cronómetro local o la
    presencia de una unidad systemd no demuestra apagado real de EC2.

Comprobación rápida reutilizable desde el equipo, con el túnel query activo:

```powershell
.tools/pwsh/pwsh.exe -File infra/aws/Test-QueryHealth.ps1 -QueryUri http://127.0.0.1:8092/api/traffic/north -MaximumAgeSeconds 30
```

En el laboratorio local se usa el puerto 18092. `-NotBefore` permite exigir un
estado posterior al T0 de la prueba; un OK antiguo causa fallo explícito.

La CA y claves de `.tools/secure-e2e` y `.tools/rabbit-security` son exclusivamente
fixtures locales. Nunca copiarlas a AWS para reutilizar las pruebas.

## Evidencia mínima para aceptar el despliegue

- Inventario cinco EC2 y IDs de imágenes, zona/puertos/SSM verificados.
- Conteo anterior/posterior y observaciones de T0; consulta actual y requestIds.
- Mensaje WebSocket y alerta realmente entregada a un consumidor de prueba.
- Resultados negativos específicos JWT/ACL/CA/hostname/plaintext y permisos BD.
- Prueba tras vencimiento JWT y recuperación con volúmenes persistentes.
- Estado de timer, prueba Stopped y recursos residuales/costos.

No se considera aprobado si alguno falla. Las limitaciones de laboratorio
(una AZ/nodo, datos artificiales, sin grafo vial completo/HA) permanecen visibles.
