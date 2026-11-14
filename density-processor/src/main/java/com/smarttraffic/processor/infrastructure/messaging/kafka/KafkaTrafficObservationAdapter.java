package com.smarttraffic.processor.infrastructure.messaging.kafka;

import com.smarttraffic.processor.application.model.ProcessingResult;
import com.smarttraffic.processor.application.port.in.ProcessTrafficObservationUseCase;
import com.smarttraffic.processor.domain.model.TrafficObservation;

/**
 * Adaptación de un registro crudo al caso de uso, sin listener ni offsets.
 * El llamador debe garantizar serialización por zona y conservar la clave sensorId.
 * La topología futura puede reutilizar el decoder antes de reagrupar por zona.
 */
public final class KafkaTrafficObservationAdapter {
    private final RawTrafficObservationDecoder decoder;
    private final ProcessTrafficObservationUseCase useCase;

    public KafkaTrafficObservationAdapter(RawTrafficObservationDecoder decoder, ProcessTrafficObservationUseCase useCase) {
        if (decoder == null || useCase == null) {
            throw new IllegalArgumentException("decoder y useCase son obligatorios");
        }
        this.decoder = decoder;
        this.useCase = useCase;
    }

    public ProcessingResult process(String key, byte[] value) {
        TrafficObservation observation = decoder.decode(key, value);
        ProcessingResult result = useCase.process(observation);
        if (result == null || !observation.eventId().equals(result.eventId())) {
            throw new IllegalStateException("El caso de uso no devolvió un resultado correlacionado");
        }
        return result;
    }
}
