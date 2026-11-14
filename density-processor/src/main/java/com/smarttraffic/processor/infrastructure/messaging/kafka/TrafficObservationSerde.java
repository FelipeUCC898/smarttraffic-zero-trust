package com.smarttraffic.processor.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.io.IOException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/** Valor interno de repartición; la clave sensorId original se valida antes de reagrupar. */
public final class TrafficObservationSerde implements Serde<TrafficObservation> {
    private final ObjectWriter writer;
    private final RawTrafficObservationDecoder decoder;

    public TrafficObservationSerde() {
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        writer = mapper.writerFor(TrafficObservation.class);
        decoder = new RawTrafficObservationDecoder(mapper);
    }

    @Override
    public Serializer<TrafficObservation> serializer() {
        return (topic, observation) -> {
            if (observation == null) {
                return null;
            }
            try {
                return writer.writeValueAsBytes(observation);
            } catch (IOException | IllegalArgumentException exception) {
                throw new SerializationException("No se pudo serializar la observación interna");
            }
        };
    }

    @Override
    public Deserializer<TrafficObservation> deserializer() {
        return (topic, bytes) -> {
            if (bytes == null) {
                return null;
            }
            try {
                return decoder.decodeValue(bytes);
            } catch (InvalidTrafficObservationException exception) {
                throw new SerializationException("Observación interna inválida");
            }
        };
    }
}
