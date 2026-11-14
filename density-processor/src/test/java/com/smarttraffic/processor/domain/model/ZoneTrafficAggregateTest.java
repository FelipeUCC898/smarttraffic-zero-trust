package com.smarttraffic.processor.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ZoneTrafficAggregateTest {
    private int eventSequence;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final CongestionThresholds THRESHOLDS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));
    // Estrategia artificial para probar el agregado, no fórmula oficial de densidad.
    private static final DensityCalculationStrategy TEST_STRATEGY =
            observation -> BigDecimal.valueOf(observation.vehicleCount());

    @Test
    void startsWithoutAnInventedObservationOrState() {
        assertThat(aggregate(TEST_STRATEGY).currentState()).isEmpty();
    }

    @Test
    void calculatesThroughStrategyAndReclassifiesInsteadOfCopyingTheSensorState() {
        ZoneTrafficAggregate aggregate = aggregate(TEST_STRATEGY);
        TrafficObservation observation = observation("north", START, 25);

        ZoneTrafficState state = aggregate.recordLatestObservation(observation).orElseThrow();

        assertThat(state.zoneId()).isEqualTo("north");
        assertThat(state.measuredAt()).isEqualTo(START);
        assertThat(state.density()).isEqualByComparingTo("25");
        assertThat(state.congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
        assertThat(state.version()).isNull();
        assertThat(observation.congestionState()).isEqualTo(CongestionState.DESPEJADA);
        assertThat(aggregate.currentState()).contains(state);
    }

    @Test
    void acceptsAnAlternativeStrategyWithoutChangingTheAggregate() {
        TrafficObservation observation = observation("north", START, 25);
        ZoneTrafficAggregate first = aggregate(TEST_STRATEGY);
        ZoneTrafficAggregate second = aggregate(ignored -> new BigDecimal("5"));

        assertThat(first.recordLatestObservation(observation).orElseThrow().congestionState())
                .isEqualTo(CongestionState.CONGESTIONADA);
        assertThat(second.recordLatestObservation(observation).orElseThrow().congestionState())
                .isEqualTo(CongestionState.DESPEJADA);
    }

    @Test
    void appliesEscalationAndRecoveryAcrossConsecutiveObservations() {
        ZoneTrafficAggregate aggregate = aggregate(TEST_STRATEGY);

        assertThat(aggregate.recordLatestObservation(observation("north", START, 5)).orElseThrow().congestionState())
                .isEqualTo(CongestionState.DESPEJADA);
        assertThat(aggregate.recordLatestObservation(observation("north", START.plusSeconds(1), 10)).orElseThrow().congestionState())
                .isEqualTo(CongestionState.MODERADA);
        assertThat(aggregate.recordLatestObservation(observation("north", START.plusSeconds(2), 20)).orElseThrow().congestionState())
                .isEqualTo(CongestionState.CONGESTIONADA);
        assertThat(aggregate.recordLatestObservation(observation("north", START.plusSeconds(3), 0)).orElseThrow().congestionState())
                .isEqualTo(CongestionState.DESPEJADA);
    }

    @Test
    void ignoresAnExactReplayWithoutCalculatingAgain() {
        AtomicInteger calculations = new AtomicInteger();
        ZoneTrafficAggregate aggregate = aggregate(observation -> {
            calculations.incrementAndGet();
            return BigDecimal.ONE;
        });
        TrafficObservation observation = observation("north", START, 1);
        ZoneTrafficState accepted = aggregate.recordLatestObservation(observation).orElseThrow();

        assertThat(aggregate.recordLatestObservation(observation)).isEmpty();
        assertThat(calculations.get()).isEqualTo(1);
        assertThat(aggregate.currentState()).contains(accepted);
    }

    @Test
    void rejectsConflictingReuseOfTheLastEventIdWithoutChangingState() {
        ZoneTrafficAggregate aggregate = aggregate(TEST_STRATEGY);
        TrafficObservation original = observation("north", START, 1);
        ZoneTrafficState accepted = aggregate.recordLatestObservation(original).orElseThrow();
        TrafficObservation conflicting = new TrafficObservation(original.eventId(), original.sensorId(),
                "north", START.plusSeconds(1), 99, BigDecimal.ONE, CongestionState.DESPEJADA);

        assertThatIllegalArgumentException().isThrownBy(() -> aggregate.recordLatestObservation(conflicting))
                .withMessageContaining("eventId");
        assertThat(aggregate.currentState()).contains(accepted);
    }

    @Test
    void ignoresOlderAndEqualTimestampsWithoutInvokingStrategy() {
        AtomicInteger calculations = new AtomicInteger();
        ZoneTrafficAggregate aggregate = aggregate(observation -> {
            calculations.incrementAndGet();
            return BigDecimal.ONE;
        });
        ZoneTrafficState accepted = aggregate.recordLatestObservation(observation("north", START, 1)).orElseThrow();

        assertThat(aggregate.recordLatestObservation(observation("north", START.minusSeconds(1), 20))).isEmpty();
        assertThat(aggregate.recordLatestObservation(observation("north", START, 30))).isEmpty();
        assertThat(calculations.get()).isEqualTo(1);
        assertThat(aggregate.currentState()).contains(accepted);
    }

    @Test
    void rejectsMissingObservationsAndAnotherZoneBeforeCalculating() {
        AtomicInteger calculations = new AtomicInteger();
        ZoneTrafficAggregate aggregate = aggregate(observation -> {
            calculations.incrementAndGet();
            return BigDecimal.ONE;
        });

        assertThatIllegalArgumentException().isThrownBy(() -> aggregate.recordLatestObservation(null));
        assertThatIllegalArgumentException().isThrownBy(() -> aggregate.recordLatestObservation(
                observation("south", START, 1))).withMessageContaining("zoneId");
        assertThat(calculations.get()).isZero();
        assertThat(aggregate.currentState()).isEmpty();
    }

    @Test
    void keepsThePreviousStateWhenTheStrategyFailsAndAllowsRetry() {
        ZoneTrafficAggregate aggregate = aggregate(observation -> {
            if (observation.vehicleCount() == 99) {
                throw new IllegalStateException("Fallo de cálculo de prueba");
            }
            return BigDecimal.valueOf(observation.vehicleCount());
        });
        ZoneTrafficState accepted = aggregate.recordLatestObservation(observation("north", START, 5)).orElseThrow();

        assertThatThrownBy(() -> aggregate.recordLatestObservation(observation("north", START.plusSeconds(1), 99)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(aggregate.currentState()).contains(accepted);
        assertThat(aggregate.recordLatestObservation(observation("north", START.plusSeconds(1), 20)))
                .hasValueSatisfying(state -> assertThat(state.congestionState()).isEqualTo(CongestionState.CONGESTIONADA));
    }

    @Test
    void rejectsNullOrNegativeStrategyResultsBeforeUpdatingTheAggregate() {
        ZoneTrafficAggregate aggregate = aggregate(observation -> {
            if (observation.vehicleCount() == 98) {
                return null;
            }
            return BigDecimal.valueOf(-observation.vehicleCount());
        });
        ZoneTrafficState accepted = aggregate.recordLatestObservation(observation("north", START, 0)).orElseThrow();

        assertThatIllegalArgumentException().isThrownBy(() -> aggregate.recordLatestObservation(
                observation("north", START.plusSeconds(1), 98))).withMessageContaining("density");
        assertThatIllegalArgumentException().isThrownBy(() -> aggregate.recordLatestObservation(
                observation("north", START.plusSeconds(1), 1))).withMessageContaining("density");
        assertThat(aggregate.currentState()).contains(accepted);
    }

    @Test
    void requiresIdentityStrategyAndThresholdsAtConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficAggregate(null, TEST_STRATEGY, THRESHOLDS));
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficAggregate(" ", TEST_STRATEGY, THRESHOLDS));
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficAggregate("z".repeat(129), TEST_STRATEGY, THRESHOLDS));
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficAggregate("north", null, THRESHOLDS));
        assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTrafficAggregate("north", TEST_STRATEGY, null));
    }

    private ZoneTrafficAggregate aggregate(DensityCalculationStrategy strategy) {
        return new ZoneTrafficAggregate("north", strategy, THRESHOLDS);
    }

    private TrafficObservation observation(String zone, Instant measuredAt, int count) {
        return new TrafficObservation(new UUID(0, ++eventSequence), "sensor-1", zone, measuredAt,
                count, BigDecimal.ONE, CongestionState.DESPEJADA);
    }
}
