# Acceso de revisión sin claves permanentes — 2026-11-16

**Acceso ya disponible:** el propietario autenticó `smarttraffic-project` con
el rol temporal AccountFullAccessRole. No crear otro usuario ni repetir el alta
por esta guía. STS, plan PAID y organización existente están documentados en
[FEASIBILITY.md](FEASIBILITY.md); las instrucciones siguientes son preparación
histórica, no acciones pendientes autorizadas. El rol se utiliza solo para lectura.

Decisión del propietario: conservar **Free Plan**, presupuesto adicional **USD 0**.
Saldo comunicado por el propietario: **USD 100**, vencimiento todavía desconocido.
Estos datos no se han verificado por API. Ninguna autorización de consumo o
despliegue está concedida. No habilitar SSO, Organizations ni Paid Plan.

## Método elegido y límites

AWS CLI instalada: 2.34.49. `aws login` requiere 2.32.0 o posterior y admite
usuarios IAM con acceso de consola. Obtiene credenciales temporales, con
renovación de sesión de hasta 12 horas; no necesita access keys de larga duración.
No ejecutar login como root ni usar credenciales del perfil `default`.

[AWS: login con credenciales de consola](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html).
[Política oficial SignInLocalDevelopmentAccess](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/SignInLocalDevelopmentAccess.html).

IAM no tiene cargo adicional. Esto no concede cobertura gratuita a EC2/EBS/IPv4.
La disponibilidad del acceso de consola IAM debe comprobarse en esta cuenta:
la nueva experiencia de AWS puede restringir su creación. Si aparece una
solicitud de activar funciones avanzadas/cambiar de plan, detenerse; no aceptar.
[Creación y restricciones de usuarios IAM](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_create.html).

SSO para acceso a cuentas requiere una instancia de organización. Crear
Organizations desde Free Plan provoca cambio a Paid Plan y caducidad de créditos
Free Tier según la documentación actual. Una instancia de cuenta para aplicaciones
no sustituye el acceso SSO a cuentas.
[Advertencia oficial](https://docs.aws.amazon.com/singlesignon/latest/userguide/enable-identity-center.html).

## Intervención del propietario en la consola

1. Entrar en la cuenta **223455088753**. Mantener MFA de root y no crear claves root.
2. En **IAM → Users**, revisar usuarios existentes. Reutilizar solamente una identidad
   personal de proyecto con permisos revisados; no compartir un usuario del equipo.
   No existe sesión CLI autorizada para realizar esta consulta desde este computador.
3. Si no hay identidad adecuada, **Create user**, nombre propuesto
   `smarttraffic-reviewer`, habilitar acceso a Management Console y elegir usuario
   IAM cuando se ofrezca Identity Center. No seleccionar ni crear Organizations.
   Contraseña inicial y cualquier cambio se manejan directamente en la consola.
4. Crear un grupo `smarttraffic-review`. En **IAM → Policies → Create policy → JSON**,
   pegar [iam/smarttraffic-review.json](iam/smarttraffic-review.json), nombre
   `SmartTrafficReview`. Adjuntarla al grupo junto con la política administrada
   **SignInLocalDevelopmentAccess**. Añadir el usuario al grupo.
5. Completar cambio de contraseña si corresponde (la consola puede asignar la
   política de cambio propio). Registrar MFA para ese usuario desde **Security
   credentials → Assign MFA device**, con intervención del titular. No compartir
   contraseña inicial, QR, códigos, secretos ni recovery codes por chat.
6. Revisar que no herede `AdministratorAccess`, `PowerUserAccess` u otros permisos
   de escritura. Esta política permite lectura específica para plan/inventario;
   no permite RunInstances, StartInstances, PassRole, modificar IAM, SSM RunCommand,
   crear presupuestos ni aplicar Terraform. No es una barrera contra permisos
   adicionales: revisar todas las políticas del usuario/grupos.
7. Abrir una sesión de navegador separada, iniciar como **IAM user** con cuenta
   `223455088753`, nombre real del usuario y MFA. Mantener fuera de esa sesión el
   acceso root para que `aws login` no seleccione accidentalmente root.

El JSON está preparado, pero su aceptación por IAM y permisos efectivos están
pendientes de validación real. Ante AccessDenied, revisar la acción concreta;
no ampliar a AdministratorAccess. Conservar la administración de identidades y
la revisión de facturación en la consola del propietario.

## Configuración local una vez lista la identidad

Codex ejecutará los comandos siguientes tras confirmarse que el usuario existe.
No escribir contraseñas ni tokens en argumentos. El navegador recibirá el login:

```powershell
aws configure set region us-east-2 --profile smarttraffic-project
aws configure set output json --profile smarttraffic-project
aws login --profile smarttraffic-project
aws sts get-caller-identity --profile smarttraffic-project --region us-east-2
```

Elegir expresamente la identidad IAM. El resultado STS debe tener Account
`223455088753` y ARN de usuario IAM (o futuro rol autorizado), nunca `:root`.
La caché temporal vive en `%USERPROFILE%\.aws\login\cache`; no copiarla al repo,
a Docker ni a otra máquina. No usar `aws configure` para introducir access keys.
No modificar `default` ni sustituir sus credenciales. Cierre de la sesión del
proyecto, cuando se solicite: `aws logout --profile smarttraffic-project`.

Después de STS ejecutar [Get-Readiness.ps1](Get-Readiness.ps1). La consulta de
Free Tier puede estar restringida o no representar créditos promocionales
externos; comprobar vencimiento y condiciones también en Billing → Credits.
No llamar Cost Explorer para esta revisión: sus APIs pueden tener costo.
Preparar presupuesto de costo cero y notificaciones desde Billing con el correo
del propietario, sin habilitar acciones de presupuesto de pago. No se ha creado
ningún presupuesto; una notificación no limita el consumo.

Si el login IAM no está disponible bajo este Free Plan, conservar las pruebas
locales y la revisión manual de Billing. No cambiar de plan ni generar claves
permanentes como sustitución automática. STS AssumeRole/GetSessionToken necesitan
una credencial fuente autorizada y no resuelven por sí solos este arranque.
