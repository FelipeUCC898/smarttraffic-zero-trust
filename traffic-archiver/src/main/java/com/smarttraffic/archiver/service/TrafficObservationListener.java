package com.smarttraffic.archiver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.archiver.dto.TrafficObservationEvent;
import com.smarttraffic.archiver.model.TrafficObservation;
import com.smarttraffic.archiver.repository.TrafficObservationRepository;
import java.io.IOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TrafficObservationListener {
    private final ObjectMapper mapper;
    private final TrafficObservationRepository repository;

    public TrafficObservationListener(ObjectMapper mapper, TrafficObservationRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @KafkaListener(topics = "${traffic.kafka.topic:raw_traffic_data}")
    public void archive(ConsumerRecord<String, byte[]> record) {
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
        TrafficObservation observation = TrafficObservation.from(event, record.key());
        repository.archive(observation.eventId(), observation.sensorId(), observation.zoneId(),
                observation.measuredAt(), observation.vehicleCount(), observation.averageSpeedKmh(),
                observation.congestionState().name());
    }
}
