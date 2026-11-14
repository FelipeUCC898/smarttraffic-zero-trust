package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ZoneTrafficSnapshotTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final CongestionThresholds LIMITS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));

    @Test
    void representsAnEmptyZoneWithItsExplicitThresholds() {
        var snapshot = new ZoneTrafficSnapshot("north", LIMITS, null, null);
        assertThat(snapshot.zoneId()).isEqualTo("north");
        assertThat(snapshot.thresholds()).isEqualTo(LIMITS);
        assertThat(snapshot.latestObservation()).isNull();
        assertThat(snapshot.latestState()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsInvalidZoneIdentifiers(String zoneId) {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot(zoneId, LIMITS, null, null));
    }

    @Test
    void rejectsOversizedZoneAndMissingThresholds() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("x".repeat(129), LIMITS, null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("north", null, null, null));
    }

    @Test
    void rejectsIncompleteAndCrossZoneSnapshots() {
        var observation = observation("north", TIME);
        var state = state("north", TIME, "10", CongestionState.MODERADA);
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("north", LIMITS, observation, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("north", LIMITS, null, state));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("south", LIMITS, observation, state));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("north", LIMITS, observation("south", TIME), state));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ZoneTrafficSnapshot("north", LIMITS, observation,
                        state("south", TIME, "10", CongestionState.MODERADA)));
    }

    @Test
    void rejectsDifferentObservationAndStateTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficSnapshot("north", LIMITS,
                observation("north", TIME), state("north", TIME.plusSeconds(1), "10", CongestionState.MODERADA)));
    }

    @ParameterizedTest
    @CsvSource({"0,DESPEJADA", "9.99,DESPEJADA", "10,MODERADA", "19.99,MODERADA",
            "20,CONGESTIONADA", "100,CONGESTIONADA"})
    void acceptsStatesAtAndBetweenExplicitThresholds(String density, CongestionState label) {
        var state = state("north", TIME, density, label);
        var snapshot = new ZoneTrafficSnapshot("north", LIMITS, observation("north", TIME), state);
        assertThat(snapshot.latestState()).isEqualTo(state);
    }

    @ParameterizedTest
    @CsvSource({"0,MODERADA", "10,DESPEJADA", "20,MODERADA", "20,DESPEJADA"})
    void rejectsStatesInconsistentWithTheCapturedThresholds(String density, CongestionState label) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficSnapshot("north", LIMITS,
                observation("north", TIME), state("north", TIME, density, label)));
    }

    private static TrafficObservation observation(String zone, Instant time) {
        return new TrafficObservation(new UUID(0, 1), "sensor-1", zone, time,
                12, new BigDecimal("35.50"), CongestionState.DESPEJADA);
    }

    private static ZoneTrafficState state(String zone, Instant time, String density, CongestionState label) {
        return new ZoneTrafficState(zone, time, new BigDecimal(density), label, "opaque-version");
    }
}
