# Reglas nftables

Estas reglas son una guía de implementación para las VMs. Sustituir los marcadores de IP por las direcciones reales de la topología antes de aplicarlas.

Reglas obligatorias:

1. Política `drop` para `input` y `output`.
2. Permitir `lo`.
3. Permitir conexiones `established,related`.
4. Permitir únicamente los puertos y orígenes definidos en `infra/README.md`.
5. No exponer Kafka, RabbitMQ ni PostgreSQL a interfaces externas.

Validación recomendada:

```bash
sudo nft list ruleset
sudo nft -c -f /etc/nftables.conf
```
