package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ZoneTrafficAggregateRestoreTest {
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    // Fórmula y umbrales exclusivamente artificiales para verificar reconstrucción.
    private static final CongestionThresholds LIMITS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));
    private static final DensityCalculationStrategy STRATEGY =
            observation -> BigDecimal.valueOf(observation.vehicleCount());

    @Test
    void restoresAnEmptyZoneAndItsIdentity() {
        var original = new ZoneTrafficAggregate("north", STRATEGY, LIMITS);
        var snapshot = original.snapshot();
        var restored = ZoneTrafficAggregate.restore(snapshot, STRATEGY);
        assertThat(restored.snapshot()).isEqualTo(snapshot);
        assertThat(restored.currentState()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> restored.recordLatestObservation(
                new TrafficObservation(new UUID(0, 1), "sensor-1", "south", TIME,
                        1, BigDecimal.ONE, CongestionState.DESPEJADA)));
    }

    @ParameterizedTest
    @CsvSource({"0,DESPEJADA", "10,MODERADA", "20,CONGESTIONADA"})
    void restoresEveryStateWithoutInvokingTheStrategy(int count, CongestionState expected) {
        var original = aggregate();
        original.recordLatestObservation(observation(1, TIME, count));
        var captured = original.snapshot();
        var restored = ZoneTrafficAggregate.restore(captured, ignored -> {
            throw new AssertionError("Restaurar no debe recalcular");
        });
        assertThat(restored.snapshot()).isEqualTo(captured);
        assertThat(restored.currentState().orElseThrow().congestionState()).isEqualTo(expected);
    }

    @Test
    void preservesReplayConflictAndTemporalChecksAfterReconstruction() {
        var original = aggregate();
        var last = observation(1, TIME, 12);
        original.recordLatestObservation(last);
        var restored = ZoneTrafficAggregate.restore(original.snapshot(), STRATEGY);
        assertThat(restored.recordLatestObservation(last)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(
                () -> restored.recordLatestObservation(observation(1, TIME.plusSeconds(1), 15)));
        assertThat(restored.recordLatestObservation(observation(2, TIME.minusSeconds(1), 15))).isEmpty();
        assertThat(restored.recordLatestObservation(observation(3, TIME, 15))).isEmpty();
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
    }

    @ParameterizedTest
    @CsvSource({"0,20,CONGESTIONADA", "20,10,MODERADA", "10,0,DESPEJADA"})
    void continuesTransitionsWithoutMutatingTheCapturedOrOriginalState(
            int initial, int next, CongestionState expected) {
        var original = aggregate();
        original.recordLatestObservation(observation(1, TIME, initial));
        var captured = original.snapshot();
        var restored = ZoneTrafficAggregate.restore(captured, STRATEGY);
        var state = restored.recordLatestObservation(observation(2, TIME.plusSeconds(1), next)).orElseThrow();
        assertThat(state.congestionState()).isEqualTo(expected);
        assertThat(original.snapshot()).isEqualTo(captured);
        assertThat(restored.snapshot()).isNotEqualTo(captured);
    }

    @Test
    void preservesRestoredDataWhenTheNextCalculationFails() {
        var original = aggregate();
        original.recordLatestObservation(observation(1, TIME, 12));
        var captured = original.snapshot();
        var failure = new IllegalStateException("Cálculo no disponible");
        var restored = ZoneTrafficAggregate.restore(captured, ignored -> { throw failure; });
        assertThatThrownBy(() -> restored.recordLatestObservation(observation(2, TIME.plusSeconds(1), 20)))
                .isSameAs(failure);
        assertThat(restored.snapshot()).isEqualTo(captured);
    }

    @Test
    void copyingARestoredAggregateKeepsIndependentUpdates() {
        var original = aggregate();
        original.recordLatestObservation(observation(1, TIME, 12));
        var restored = ZoneTrafficAggregate.restore(original.snapshot(), STRATEGY);
        var copy = restored.copyForProcessing();
        copy.recordLatestObservation(observation(2, TIME.plusSeconds(1), 20));
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThat(copy.currentState().orElseThrow().congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
    }

    @Test
    void requiresSnapshotAndCalculationDependency() {
        assertThatIllegalArgumentException().isThrownBy(() -> ZoneTrafficAggregate.restore(null, STRATEGY));
        assertThatIllegalArgumentException().isThrownBy(
                () -> ZoneTrafficAggregate.restore(aggregate().snapshot(), null));
    }

    private static ZoneTrafficAggregate aggregate() {
        return new ZoneTrafficAggregate("north", STRATEGY, LIMITS);
    }

    private static TrafficObservation observation(long id, Instant time, int count) {
        return new TrafficObservation(new UUID(0, id), "sensor-1", "north", time,
                count, new BigDecimal("35.50"), CongestionState.DESPEJADA);
    }
}
