# Costos y lista de verificación hasta el domingo 22

Presupuesto adicional: **USD 0**. El propietario está dispuesto a utilizar sus
USD 100 promocionales para el laboratorio, pero **el despliegue aún necesita
autorización explícita**. Cuenta PAID/ACTIVE: los créditos no son un límite duro
ni garantizan cobertura de impuestos u otros conceptos.

## Estimación base

Tarifas consultadas en Price List de AWS para Ohio en esta estación:

| Concepto | Uso | Subtotal USD |
|---|---|---:|
| EC2 Linux On-Demand | 3 t3.small + m7i-flex.large + c7i-flex.large | 0.24295/h |
| IPv4 automáticas en uso | 5 × 0.005/h | 0.02500/h |
| gp3 estándar, baseline IOPS/throughput | 120 GiB × 0.08/GiB-mes | 9.60/mes |

Fórmula: `0.26795 × horas encendidas + 9.60 × horas de discos / 730`.
Los discos cuentan desde creación hasta eliminación, aunque EC2 esté detenida.

Se interpreta «hasta el domingo» como cierre **22/11/2026 23:59 Bogotá**, con
corte a las **00:00 del 23/11 (05:00 UTC)**. Esta hora es propuesta para revisión,
no autorización de ejecución. Ejemplos con todos los discos retenidos hasta el corte:

| Ventana / operación | Horas EC2 | Costo base USD |
|---|---:|---:|
| 16/11 17:30 Bogotá → corte, continuo | 150.5 | 42.31 |
| Semana completa 16/11 00:00 → corte, continuo (cota temporal conservadora) | 168 | 47.23 |
| Misma semana, 2 horas al día por 7 días | 14 | 5.96 |
| Misma semana, 8 horas al día por 7 días | 56 | 17.21 |

La cota de USD 47.23 cubre solo el **consumo base temporal** de estas cinco VMs
durante esa semana; no es cota de factura total ni saldo autorizado a gastar.
Se excluyen transferencia, snapshots, logs, servicios adicionales, impuestos y
uso de otras cuentas/cargas. Con inicio posterior, calcular otra vez:

```powershell
.tools/pwsh/pwsh.exe -File infra/aws/Get-LabCost.ps1
# Escenario de 14h encendido, discos toda la semana:
.tools/pwsh/pwsh.exe -File infra/aws/Get-LabCost.ps1 -From 2026-11-16T00:00:00-05:00 -RunningHours 14
# Ejemplo de transferencia adicional FACTURABLE, sin descontar beneficios no verificados:
.tools/pwsh/pwsh.exe -File infra/aws/Get-LabCost.ps1 -ExtraTransferGiB 10 -TransferUsdPerGiB 0.09
```

Transferencia: usar IP privadas entre VMs en la misma AZ reduce costos; esa
transferencia EC2 privada en la misma AZ no tiene cargo según precios EC2.
Entradas desde Internet normalmente no tienen cargo de transferencia. AWS ofrece
100 GB/mes de salida a Internet agregados entre servicios/regiones elegibles;
su disponibilidad para esta cuenta/organización **no se verificó**. No descontarlos
por defecto. La tarifa 0.09/GiB del ejemplo es un escenario orientativo de salida
EC2 Ohio, no una tarifa universal para SSM, todas las rutas o todos los destinos.
Evitar replicación entre AZ, salida pública entre VMs, snapshots e imágenes en
servicios AWS que no se hayan aprobado. No usar Spot: una interrupción complica
la demostración y no asegura recuperación de este laboratorio de un nodo.

## Verificar exactamente los USD 100

La API `freetier get-account-plan-state` devolvió 100 USD y PAID/ACTIVE, sin fecha
de vencimiento. Eso no prueba los servicios aplicables ni el crédito dedicado a
SmartTraffic. La CLI instalada 2.34.49 no reconoce `aws billing get-credits`.
AWS documenta actualmente la API GetCredits; no se instaló/actualizó la CLI ni
se ampliaron permisos para usarla. La vía verificable pendiente es la consola:

1. Abrir [Billing and Cost Management → Credits](https://console.aws.amazon.com/billing/home#/credits)
   en la cuenta correcta, con el acceso temporal autorizado, sin root.
2. Elegir el crédito individual de USD 100 y abrir **Credit details**.
3. Registrar, sin publicar códigos de canje ni datos privados: nombre/tipo, estado
   activo, fecha inicial, **expiration/end date**, original, restante y estimado.
4. En **Applicable services/products**, confirmar EC2 (incluido EBS y sus conceptos)
   y VPC/IPv4 según la lista que muestre ese crédito. Verificar cargos excluidos.
5. Revisar **Sharing preferences** y application history: en una organización,
   otras cuentas pueden consumir créditos compartidos. No modificar las preferencias.
6. Abrir **Bills**, mes noviembre 2026, y revisar cargos por servicio/región,
   créditos aplicados e impuestos. Distinguir importe bruto de total por pagar.
7. Si no hay acceso, solicitar al administrador de Billing la vista/detalle;
   no habilitar root, Organizations, Cost Explorer ni ampliar permisos automáticamente.

Los saldos/detalles estimados y Bills pueden tener demoras. Antes de aprobar,
verificar que la vigencia llega al cierre, que la cobertura incluye el laboratorio
y que queda reserva para transferencia/impuestos/usos ajenos. Una reserva sugerida
de al menos USD 40 frente al consumo base de la semana **no controla** los gastos
compartidos ni garantiza USD 0 adicionales. Si no se confirma cobertura, no aplicar.

## Checklist de costos antes de arrancar

- [ ] Cuenta 223455088753 y región us-east-2; sesión temporal no root.
- [ ] Saldo, vencimiento posterior al cierre y servicios elegibles comprobados en Billing.
- [ ] Compartición/otros consumos de la organización revisados por su administrador.
- [ ] Plan revisado de 53 recursos, sin extras y sin cambios a SCP/Organizations/plan.
- [ ] Ventana y máximo de consumo que se autoriza definidos por el propietario.
- [ ] Alertas de costo preparadas/aprobadas; confirmar recepción después de crearlas.
- [ ] T3 usa Standard, no Unlimited; gp3 con IOPS/throughput base; monitoring detallado no habilitado.
- [ ] Sin NAT, EIP, endpoints de interfaz, ELB, RDS/MQ/MSK, ECR/S3 ni logs pagos añadidos.
- [ ] Bundles locales, SSM y puertos privados; no construir Java dentro de EC2 pequeñas.
- [ ] Timer por arranque y cutoff absoluto revisados; nunca presentarlos como límite de gasto.

## Checklist al detener o eliminar

- [ ] Cinco EC2 Stopped o Terminated; ninguna Pending/Running inesperada.
- [ ] EBS: VolumeId, tamaño, tags, estado y decisión de conservar/borrar; revisar discos huérfanos.
- [ ] Snapshots/AMIs: inventario propio y decisión explícita; retención sigue costando.
- [ ] Elastic IPs: no basta desasociar; liberación requiere autorización. IPv4 automáticas se liberan al Stop.
- [ ] NAT Gateways, interface endpoints y balanceadores: verificar que no existan extras.
- [ ] RDS, Amazon MQ, MSK y otros servicios: revisar recursos fuera de Terraform si los hubo.
- [ ] S3, CloudWatch Logs y servicios de backup/exportación: revisar si fueron habilitados por otra vía.
- [ ] Otras regiones: lecturas denegadas por SCP fuera de Ohio; obtener inventario autorizado, sin cambiar SCP.
- [ ] Billing: verificar evolución de cargos/créditos/impuestos tras actualizarse los datos.
- [ ] Confirmar que el estado Terraform y evidencia se conservaron localmente fuera de Git.

## Apagado del domingo, preparado sin activación

El bootstrap prepara un timer systemd de calendario para `2026-11-23 05:00:00 UTC`
y un guard por arranque que apaga si la fecha ya pasó. Mantiene además el timer
de 60 minutos por boot. No crea recursos AWS adicionales. **Nada está instalado
en EC2 hasta aprobar y aplicar Terraform.** Su activación forma parte del plan que
debe aprobarse, no de estas verificaciones locales.

Depende del SO, hora y systemd; no funciona si esos componentes fallan. No es
una garantía de gasto máximo y no borra EBS. Se podría preparar un Scheduler AWS
externo más adelante, pero requeriría recursos/IAM/costos y autorización adicional;
no se ha creado ni habilitado. El domingo comprobar manualmente Stopped y completar
la lista de recursos residuales. Consultar [POWER-GUIDE.md](POWER-GUIDE.md) y,
si se aprueba el borrado, [TEARDOWN.md](TEARDOWN.md).

Fuentes: [EC2 y transferencia](https://aws.amazon.com/ec2/pricing/on-demand/),
[EBS](https://aws.amazon.com/ebs/pricing/), [IPv4](https://aws.amazon.com/vpc/pricing/),
[créditos y aplicación](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/useconsolidatedbilling-credits.html),
[GetCredits](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_billing_GetCredits.html).
