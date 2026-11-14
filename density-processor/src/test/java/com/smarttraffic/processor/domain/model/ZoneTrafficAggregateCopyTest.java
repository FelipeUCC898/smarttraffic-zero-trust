package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneTrafficAggregateCopyTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    // Configuración artificial, deliberadamente distinta a los tests de R1.
    private static final CongestionThresholds THRESHOLDS =
            new CongestionThresholds(new BigDecimal("3"), new BigDecimal("7"));
    private static final DensityCalculationStrategy STRATEGY =
            observation -> BigDecimal.valueOf(observation.vehicleCount()).multiply(new BigDecimal("2"));

    @Test
    void copiesAnEmptyAggregateAndPreservesItsZoneInvariant() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);

        ZoneTrafficAggregate copy = original.copyForProcessing();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.currentState()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> copy.recordLatestObservation(
                observation(1, "south", START, 1))).withMessageContaining("zoneId");
        assertThat(copy.currentState()).isEmpty();
        assertThat(original.currentState()).isEmpty();
        assertThat(copy.recordLatestObservation(observation(2, "north", START, 2))).isPresent();
        assertThat(original.currentState()).isEmpty();
    }

    @Test
    void preservesConfirmedStateStrategyAndThresholdsInTheCopy() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        ZoneTrafficState confirmed = original.recordLatestObservation(
                observation(1, "north", START, 2)).orElseThrow();

        ZoneTrafficAggregate copy = original.copyForProcessing();
        ZoneTrafficState next = copy.recordLatestObservation(
                observation(2, "north", START.plusSeconds(1), 4)).orElseThrow();

        assertThat(confirmed.density()).isEqualByComparingTo("4");
        assertThat(confirmed.congestionState()).isEqualTo(CongestionState.MODERADA);
        assertThat(next.zoneId()).isEqualTo("north");
        assertThat(next.density()).isEqualByComparingTo("8");
        assertThat(next.congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
        assertThat(original.currentState()).contains(confirmed);
        assertThat(copy.currentState()).contains(next);
    }

    @Test
    void retainsTheLastEventIncludingReplayAndConflictingReuseChecks() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        TrafficObservation last = observation(1, "north", START, 2);
        ZoneTrafficState confirmed = original.recordLatestObservation(last).orElseThrow();

        ZoneTrafficAggregate copy = original.copyForProcessing();

        assertThat(copy.currentState()).contains(confirmed);
        assertThat(copy.recordLatestObservation(last)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> copy.recordLatestObservation(
                observation(1, "north", START.plusSeconds(1), 4))).withMessageContaining("eventId");
        assertThat(copy.currentState()).contains(confirmed);
        assertThat(original.recordLatestObservation(last)).isEmpty();
        assertThat(original.currentState()).contains(confirmed);
    }

    @Test
    void processingTheCopyDoesNotConsumeTheEventInTheOriginal() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        original.recordLatestObservation(observation(1, "north", START, 2));
        ZoneTrafficAggregate copy = original.copyForProcessing();
        TrafficObservation next = observation(2, "north", START.plusSeconds(1), 4);

        ZoneTrafficState copiedResult = copy.recordLatestObservation(next).orElseThrow();

        assertThat(original.recordLatestObservation(next)).contains(copiedResult);
        assertThat(copy.recordLatestObservation(next)).isEmpty();
    }

    @Test
    void laterChangesInTheOriginalDoNotChangeTheCopy() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        ZoneTrafficState confirmed = original.recordLatestObservation(
                observation(1, "north", START, 4)).orElseThrow();
        ZoneTrafficAggregate copy = original.copyForProcessing();

        original.recordLatestObservation(observation(2, "north", START.plusSeconds(2), 0));

        assertThat(copy.currentState()).contains(confirmed);
        assertThat(copy.recordLatestObservation(observation(3, "north", START.plusSeconds(1), 2)))
                .hasValueSatisfying(state -> assertThat(state.congestionState()).isEqualTo(CongestionState.MODERADA));
        assertThat(original.currentState()).hasValueSatisfying(state -> {
            assertThat(state.measuredAt()).isEqualTo(START.plusSeconds(2));
            assertThat(state.congestionState()).isEqualTo(CongestionState.DESPEJADA);
        });
    }

    @Test
    void aCalculationFailureInTheCopyPreservesBothConfirmedStatesAndAllowsRetry() {
        ZoneTrafficAggregate original = aggregate(observation -> {
            if (observation.vehicleCount() == 99) {
                throw new IllegalStateException("Fallo de cálculo de prueba");
            }
            return STRATEGY.calculate(observation);
        });
        ZoneTrafficState confirmed = original.recordLatestObservation(
                observation(1, "north", START, 2)).orElseThrow();
        ZoneTrafficAggregate copy = original.copyForProcessing();

        assertThatThrownBy(() -> copy.recordLatestObservation(
                observation(2, "north", START.plusSeconds(1), 99)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(original.currentState()).contains(confirmed);
        assertThat(copy.currentState()).contains(confirmed);
        assertThat(copy.recordLatestObservation(observation(3, "north", START.plusSeconds(1), 4))).isPresent();
        assertThat(original.currentState()).contains(confirmed);
    }

    @Test
    void inheritedTimestampRejectsOldAndEqualObservationsWithoutChangingEitherInstance() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        ZoneTrafficState confirmed = original.recordLatestObservation(
                observation(1, "north", START, 2)).orElseThrow();
        ZoneTrafficAggregate copy = original.copyForProcessing();

        assertThat(copy.recordLatestObservation(observation(2, "north", START.minusSeconds(1), 4))).isEmpty();
        assertThat(copy.recordLatestObservation(observation(3, "north", START, 4))).isEmpty();
        assertThat(copy.currentState()).contains(confirmed);
        assertThat(original.currentState()).contains(confirmed);
    }

    @Test
    void independentCopiesCanExploreDifferentUpdatesFromTheSameConfirmedState() {
        ZoneTrafficAggregate original = aggregate(STRATEGY);
        ZoneTrafficState confirmed = original.recordLatestObservation(
                observation(1, "north", START, 2)).orElseThrow();
        ZoneTrafficAggregate first = original.copyForProcessing();
        ZoneTrafficAggregate second = original.copyForProcessing();

        first.recordLatestObservation(observation(2, "north", START.plusSeconds(1), 4));
        second.recordLatestObservation(observation(3, "north", START.plusSeconds(1), 0));

        assertThat(first.currentState()).hasValueSatisfying(state ->
                assertThat(state.congestionState()).isEqualTo(CongestionState.CONGESTIONADA));
        assertThat(second.currentState()).hasValueSatisfying(state ->
                assertThat(state.congestionState()).isEqualTo(CongestionState.DESPEJADA));
        assertThat(original.currentState()).contains(confirmed);
    }

    private ZoneTrafficAggregate aggregate(DensityCalculationStrategy strategy) {
        return new ZoneTrafficAggregate("north", strategy, THRESHOLDS);
    }

    private TrafficObservation observation(long id, String zone, Instant measuredAt, int count) {
        return new TrafficObservation(new UUID(0, id), "sensor-1", zone, measuredAt,
                count, BigDecimal.ONE, CongestionState.DESPEJADA);
    }
}
