package com.smarttraffic.archiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class TrafficObservationListener {
    private final ObjectMapper mapper;
    private final TrafficObservationRepository repository;

    TrafficObservationListener(ObjectMapper mapper, TrafficObservationRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @KafkaListener(topics = "${traffic.kafka.topic:raw_traffic_data}")
    void archive(ConsumerRecord<String, byte[]> record) {
        TrafficObservationEvent event;
        try {
            if (record.value() == null) {
                throw new IllegalArgumentException("Missing traffic observation");
            }
            event = mapper.readValue(record.value(), TrafficObservationEvent.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid traffic observation JSON", exception);
        }
        if (event == null) {
            throw new IllegalArgumentException("Missing traffic observation");
        }
        event.validate(record.key());

        repository.archive(event.eventId(), event.sensorId(), event.zoneId(), event.measuredAt(),
                event.vehicleCount(), event.averageSpeedKmh(), event.congestionState().name());
    }
}
