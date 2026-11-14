package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrafficObservationSerdeTest {
    private final TrafficObservationSerde serde = new TrafficObservationSerde();

    @Test
    void preservesExactObservationDataAcrossRepartitionSerialization() {
        var reading = new TrafficObservation(new UUID(0, 1), "sensor-ñ", "north",
                Instant.parse("2026-01-01T00:00:00.123456789Z"), 12,
                new BigDecimal("35.5000000000000000001"), CongestionState.MODERADA);
        byte[] bytes = serde.serializer().serialize("test-only", reading);
        assertThat(serde.deserializer().deserialize("test-only", bytes)).isEqualTo(reading);
    }

    @Test
    void supportsNullOnlyAsTheKafkaRepresentation() {
        assertThat(serde.serializer().serialize("test-only", null)).isNull();
        assertThat(serde.deserializer().deserialize("test-only", null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "[]", "private-corrupt-value"})
    void rejectsInvalidInternalDocumentsWithoutExposingTheirContents(String body) {
        assertThatThrownBy(() -> serde.deserializer().deserialize("private-topic", body.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class).hasMessage("Observación interna inválida").hasNoCause();
    }
}
