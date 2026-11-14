package com.smarttraffic.simulator.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.application.port.out.TrafficObservationPublisher;
import com.smarttraffic.simulator.domain.model.SensorReading;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaTrafficObservationPublisher implements TrafficObservationPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTrafficObservationPublisher.class);
    private final KafkaOperations<String, byte[]> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    private final long timeoutMs;

    public KafkaTrafficObservationPublisher(KafkaOperations<String, byte[]> kafka, ObjectMapper mapper,
            String topic, long timeoutMs) {
        if (kafka == null || mapper == null || topic == null || topic.isBlank() || timeoutMs < 1) {
            throw new IllegalArgumentException("Dependencias, tópico y timeout positivo son obligatorios");
        }
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void publish(SensorReading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("reading es obligatorio");
        }
        try {
            byte[] json = mapper.writeValueAsBytes(reading);
            kafka.send(topic, reading.sensorId(), json).get(timeoutMs, TimeUnit.MILLISECONDS);
            LOGGER.info("Kafka confirmó la observación: eventId={} sensorId={} topic={}",
                    reading.eventId(), reading.sensorId(), topic);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida", exception);
        } catch (JsonProcessingException | ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("No se confirmó la publicación de la observación", exception);
        }
    }
}
