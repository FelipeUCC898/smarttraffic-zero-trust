package com.smarttraffic.simulator.infrastructure.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.domain.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalReadingLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalReadingLogger.class);
    private final ObjectMapper mapper;

    public LocalReadingLogger(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void log(SensorReading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("reading es obligatorio");
        }
        try {
            LOGGER.info("Lectura LOCAL (sin publicar en Kafka): key={} event={}",
                    reading.sensorId(), mapper.writeValueAsString(reading));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar la lectura local", exception);
        }
    }
}
