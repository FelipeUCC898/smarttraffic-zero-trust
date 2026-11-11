package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TrafficObservationTest {
    private static final UUID EVENT_ID = UUID.fromString("ca620302-913d-49de-af36-bb1173016f9c");
    private static final Instant MEASURED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void preservesObservationWithoutRoundingOrNormalizingIdentifiers() {
        TrafficObservation observation = observation(EVENT_ID, " sensor-1 ", "north", MEASURED_AT,
                42, new BigDecimal("35.50"), CongestionState.MODERADA);

        assertThat(observation.eventId()).isEqualTo(EVENT_ID);
        assertThat(observation.sensorId()).isEqualTo(" sensor-1 ");
        assertThat(observation.zoneId()).isEqualTo("north");
        assertThat(observation.measuredAt()).isEqualTo(MEASURED_AT);
        assertThat(observation.vehicleCount()).isEqualTo(42);
        assertThat(observation.averageSpeedKmh()).isEqualTo(new BigDecimal("35.50"));
        assertThat(observation.congestionState()).isEqualTo(CongestionState.MODERADA);
    }

    @Test
    void acceptsZeroMeasurementsAndIdentifiersAtTheCurrentCompatibilityLimit() {
        TrafficObservation observation = observation(EVENT_ID, "s".repeat(128), "z".repeat(128),
                MEASURED_AT, 0, BigDecimal.ZERO, CongestionState.DESPEJADA);

        assertThat(observation.vehicleCount()).isZero();
        assertThat(observation.averageSpeedKmh()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankIdentifiers(String identifier) {
        assertThatIllegalArgumentException().isThrownBy(() -> observation(EVENT_ID, identifier, "north",
                MEASURED_AT, 1, BigDecimal.ONE, CongestionState.DESPEJADA)).withMessageContaining("sensorId");
        assertThatIllegalArgumentException().isThrownBy(() -> observation(EVENT_ID, "sensor-1", identifier,
                MEASURED_AT, 1, BigDecimal.ONE, CongestionState.DESPEJADA)).withMessageContaining("zoneId");
    }

    @Test
    void rejectsIdentifiersBeyondTheCurrentCompatibilityLimit() {
        String identifier = "x".repeat(129);
        assertThatIllegalArgumentException().isThrownBy(() -> observation(EVENT_ID, identifier, "north",
                MEASURED_AT, 1, BigDecimal.ONE, CongestionState.DESPEJADA)).withMessageContaining("sensorId");
        assertThatIllegalArgumentException().isThrownBy(() -> observation(EVENT_ID, "sensor-1", identifier,
                MEASURED_AT, 1, BigDecimal.ONE, CongestionState.DESPEJADA)).withMessageContaining("zoneId");
    }

    @ParameterizedTest(name = "rechaza {0}")
    @MethodSource("invalidMeasurements")
    void rejectsMissingFieldsAndNegativeMeasurements(String field, UUID eventId, Instant measuredAt,
            Integer count, BigDecimal speed, CongestionState state) {
        assertThatIllegalArgumentException().isThrownBy(() -> observation(eventId, "sensor-1", "north",
                measuredAt, count, speed, state)).withMessageContaining(field);
    }

    private static Stream<Arguments> invalidMeasurements() {
        return Stream.of(
                Arguments.of("eventId", null, MEASURED_AT, 1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of("measuredAt", EVENT_ID, null, 1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of("vehicleCount", EVENT_ID, MEASURED_AT, null, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of("vehicleCount", EVENT_ID, MEASURED_AT, -1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of("averageSpeedKmh", EVENT_ID, MEASURED_AT, 1, null, CongestionState.MODERADA),
                Arguments.of("averageSpeedKmh", EVENT_ID, MEASURED_AT, 1, new BigDecimal("-0.01"), CongestionState.MODERADA),
                Arguments.of("congestionState", EVENT_ID, MEASURED_AT, 1, BigDecimal.ONE, null));
    }

    private TrafficObservation observation(UUID eventId, String sensorId, String zoneId, Instant measuredAt,
            Integer count, BigDecimal speed, CongestionState state) {
        return new TrafficObservation(eventId, sensorId, zoneId, measuredAt, count, speed, state);
    }
}
