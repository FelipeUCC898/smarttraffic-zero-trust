package com.smarttraffic.processor.application.port.out;

import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;

/** Puerto del estado de procesamiento de B, independiente del histórico de C. */
public interface ZoneTrafficStateStore {
    /**
     * Obtiene el agregado confirmado de la zona o crea uno vacío usando dependencias
     * de dominio explícitas. Nunca devuelve null ni inventa fórmula o umbrales.
     * El llamador no debe mutar el agregado confirmado antes de asegurar su actualización.
     * La copia de trabajo y su aislamiento se implementarán a partir de R2.2.
     *
     * @throws IllegalArgumentException si zoneId es nulo, vacío o excede el límite del dominio
     * @throws IllegalStateException si no está disponible la configuración necesaria para crear la zona
     */
    ZoneTrafficAggregate getOrCreate(String zoneId);

    /**
     * Guarda la actualización confirmada bajo su identidad de zona.
     * La clave explícita evita exigir accesores nuevos al agregado de R1 en esta etapa.
     * La implementación debe preservar la correspondencia entre clave y agregado.
     * Durabilidad, aislamiento y recuperación se definirán con el adaptador; esta
     * firma no proporciona una transacción con RabbitMQ ni confirma offsets Kafka.
     *
     * @throws IllegalArgumentException si la clave o el agregado no son válidos o no corresponden
     */
    void save(String zoneId, ZoneTrafficAggregate aggregate);
}
