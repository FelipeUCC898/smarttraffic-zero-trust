package com.smarttraffic.simulator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SensorModelsTest {
    private static final UUID EVENT_ID = new UUID(0, 1);
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final MeasurementSample SAMPLE = new MeasurementSample(0, BigDecimal.ZERO, CongestionState.DESPEJADA);

    @Test
    void preservesValuesAndAcceptsZeroAndTheCurrentIdentifierLimit() {
        SensorReading reading = new SensorReading(EVENT_ID, "s".repeat(128), "z".repeat(128),
                TIME, 0, new BigDecimal("0.00"), CongestionState.DESPEJADA);

        assertThat(reading.eventId()).isEqualTo(EVENT_ID);
        assertThat(reading.measuredAt()).isEqualTo(TIME);
        assertThat(reading.vehicleCount()).isZero();
        assertThat(reading.averageSpeedKmh()).isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankIdentifiers(String identifier) {
        assertThatIllegalArgumentException().isThrownBy(() -> reading(identifier, "north"));
        assertThatIllegalArgumentException().isThrownBy(() -> reading("sensor-1", identifier));
    }

    @Test
    void rejectsOversizedIdentifiersWithoutNormalizingThem() {
        assertThatIllegalArgumentException().isThrownBy(() -> reading("s".repeat(129), "north"));
        assertThatIllegalArgumentException().isThrownBy(() -> reading("sensor-1", "z".repeat(129)));
        assertThat(reading(" sensor-1 ", "north").sensorId()).isEqualTo(" sensor-1 ");
    }

    @ParameterizedTest
    @MethodSource("invalidReadings")
    void rejectsMissingFieldsAndNegativeMeasurements(UUID id, Instant time, Integer count,
            BigDecimal speed, CongestionState state) {
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorReading(id, "sensor-1", "north",
                time, count, speed, state));
    }

    @ParameterizedTest
    @MethodSource("invalidSamples")
    void sampleCannotBypassMeasurementValidation(Integer count, BigDecimal speed, CongestionState state) {
        assertThatIllegalArgumentException().isThrownBy(() -> new MeasurementSample(count, speed, state));
    }

    @Test
    void scenarioOwnsAnImmutableCopyOfItsSamples() {
        List<MeasurementSample> source = new ArrayList<>(List.of(SAMPLE));
        SensorScenario scenario = new SensorScenario("sensor-1", "north", source);

        source.clear();

        assertThat(scenario.samples()).containsExactly(SAMPLE);
        assertThatThrownBy(() -> scenario.samples().add(SAMPLE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidScenarioIdentityAndMissingSamples() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorScenario(null, "north", List.of(SAMPLE)));
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorScenario("sensor-1", " ", List.of(SAMPLE)));
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorScenario("sensor-1", "north", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorScenario("sensor-1", "north", List.of()));
        List<MeasurementSample> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorScenario("sensor-1", "north", withNull));
    }

    private SensorReading reading(String sensor, String zone) {
        return new SensorReading(EVENT_ID, sensor, zone, TIME, 1, BigDecimal.ONE, CongestionState.MODERADA);
    }

    private static Stream<Arguments> invalidReadings() {
        return Stream.of(
                Arguments.of(null, TIME, 1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(EVENT_ID, null, 1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(EVENT_ID, TIME, null, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(EVENT_ID, TIME, -1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(EVENT_ID, TIME, 1, null, CongestionState.MODERADA),
                Arguments.of(EVENT_ID, TIME, 1, new BigDecimal("-0.1"), CongestionState.MODERADA),
                Arguments.of(EVENT_ID, TIME, 1, BigDecimal.ONE, null));
    }

    private static Stream<Arguments> invalidSamples() {
        return Stream.of(
                Arguments.of(null, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(-1, BigDecimal.ONE, CongestionState.MODERADA),
                Arguments.of(1, null, CongestionState.MODERADA),
                Arguments.of(1, new BigDecimal("-0.1"), CongestionState.MODERADA),
                Arguments.of(1, BigDecimal.ONE, null));
    }
}
