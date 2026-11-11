package com.smarttraffic.archiver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class TrafficObservationListenerTest {
    private final TrafficObservationRepository repository = mock(TrafficObservationRepository.class);
    private final TrafficObservationListener listener =
            new TrafficObservationListener(new ObjectMapper().findAndRegisterModules(), repository);

    @Test
    void archivesAValidObservation() {
        listener.archive(record("sensor-1", validJson()));

        verify(repository).archive(UUID.fromString("ca620302-913d-49de-af36-bb1173016f9c"),
                "sensor-1", "north", Instant.parse("2026-01-01T00:00:00Z"), 42,
                new BigDecimal("35.5"), "MODERADA");
    }

    @Test
    void rejectsMissingFields() {
        assertThatThrownBy(() -> listener.archive(record("sensor-1", "{}")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsKeysThatDoNotMatchTheSensor() {
        assertThatThrownBy(() -> listener.archive(record("other-sensor", validJson())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNegativeCountsAndMalformedJson() {
        assertThatThrownBy(() -> listener.archive(record("sensor-1",
                validJson().replace("\"vehicleCount\":42", "\"vehicleCount\":-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.archive(record("sensor-1", "{")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private ConsumerRecord<String, byte[]> record(String key, String json) {
        return new ConsumerRecord<>("raw_traffic_data", 0, 0, key, json.getBytes(StandardCharsets.UTF_8));
    }

    private String validJson() {
        return """
                {"eventId":"ca620302-913d-49de-af36-bb1173016f9c","sensorId":"sensor-1","zoneId":"north",
                 "measuredAt":"2026-01-01T00:00:00Z","vehicleCount":42,
                 "averageSpeedKmh":35.5,"congestionState":"MODERADA"}
                """;
    }
}
