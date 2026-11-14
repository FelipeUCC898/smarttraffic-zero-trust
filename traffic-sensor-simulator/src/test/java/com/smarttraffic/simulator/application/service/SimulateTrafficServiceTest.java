package com.smarttraffic.simulator.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.simulator.domain.model.CongestionState;
import com.smarttraffic.simulator.domain.model.MeasurementSample;
import com.smarttraffic.simulator.domain.model.SensorReading;
import com.smarttraffic.simulator.domain.model.SensorScenario;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulateTrafficServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    void generatesOneReadingPerSensorWithUtcTimeAndDistinctIds() {
        SimulateTrafficService service = service(List.of(sensor("s1", 5, 10), sensor("s2", 20)));

        List<SensorReading> readings = service.generateBatch();

        assertThat(readings).extracting(SensorReading::sensorId).containsExactly("s1", "s2");
        assertThat(readings).extracting(SensorReading::measuredAt).containsOnly(START);
        assertThat(readings).extracting(SensorReading::eventId).doesNotHaveDuplicates();
        assertThat(readings).extracting(SensorReading::vehicleCount).containsExactly(5, 20);
        assertThatThrownBy(() -> readings.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void advancesEachScenarioAndCyclesWithoutDerivingCongestion() {
        SimulateTrafficService service = service(List.of(sensor("s1", 5, 10), sensor("s2", 20)));
        service.generateBatch();

        clock.current = START.plusSeconds(1);
        List<SensorReading> second = service.generateBatch();
        clock.current = START.plusSeconds(2);
        List<SensorReading> third = service.generateBatch();

        assertThat(second).extracting(SensorReading::vehicleCount).containsExactly(10, 20);
        assertThat(third).extracting(SensorReading::vehicleCount).containsExactly(5, 20);
        assertThat(second).extracting(SensorReading::congestionState).containsOnly(CongestionState.MODERADA);
        assertThat(second).extracting(SensorReading::eventId).doesNotContain(new UUID(0, 1), new UUID(0, 2));
    }

    @Test
    void doesNotInventTimestampsOrAdvanceSamplesWhenClockDoesNotAdvance() {
        SimulateTrafficService service = service(List.of(sensor("s1", 5, 10)));
        service.generateBatch();

        assertThat(service.generateBatch()).isEmpty();
        clock.current = START.minusSeconds(1);
        assertThat(service.generateBatch()).isEmpty();
        assertThat(sequence.get()).isEqualTo(1);
        clock.current = START.plusSeconds(1);
        assertThat(service.generateBatch()).extracting(SensorReading::vehicleCount).containsExactly(10);
    }

    @Test
    void aFailedBatchDoesNotAdvanceSamplesOrConsumeItsTimestamp() {
        AtomicInteger calls = new AtomicInteger();
        SimulateTrafficService service = new SimulateTrafficService(
                List.of(sensor("s1", 5, 10), sensor("s2", 20, 30)), clock,
                () -> calls.incrementAndGet() == 2 ? null : new UUID(0, calls.get()));

        assertThatIllegalArgumentException().isThrownBy(service::generateBatch);
        List<SensorReading> retried = service.generateBatch();

        assertThat(retried).extracting(SensorReading::vehicleCount).containsExactly(5, 20);
        assertThat(retried).extracting(SensorReading::measuredAt).containsOnly(START);
    }

    @Test
    void propagatesIdentitySupplierFailureWithoutAdvancingTheScenario() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Fallo de identidad de prueba");
        SimulateTrafficService service = new SimulateTrafficService(List.of(sensor("s1", 5, 10)), clock, () -> {
            if (calls.incrementAndGet() == 1) {
                throw failure;
            }
            return new UUID(0, calls.get());
        });

        assertThatThrownBy(service::generateBatch).isSameAs(failure);
        assertThat(service.generateBatch()).extracting(SensorReading::vehicleCount).containsExactly(5);
    }

    @Test
    void rejectsDuplicateEventIdsWithinABatch() {
        SimulateTrafficService service = new SimulateTrafficService(
                List.of(sensor("s1", 5), sensor("s2", 20)), clock, () -> new UUID(0, 1));

        assertThatIllegalArgumentException().isThrownBy(service::generateBatch).withMessageContaining("duplicado");
    }

    @Test
    void rejectsDuplicateSensorsAndMissingDependencies() {
        SensorScenario sensor = sensor("s1", 5);
        assertThatIllegalArgumentException().isThrownBy(() -> service(List.of(sensor, sensor)));
        assertThatIllegalArgumentException().isThrownBy(() -> service(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> service(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulateTrafficService(List.of(sensor), null, UUID::randomUUID));
        assertThatIllegalArgumentException().isThrownBy(() -> new SimulateTrafficService(List.of(sensor), clock, null));
        List<SensorScenario> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> service(withNull));
    }

    @Test
    void protectsTheConfiguredSensorsFromExternalListChanges() {
        List<SensorScenario> source = new ArrayList<>(List.of(sensor("s1", 5)));
        SimulateTrafficService service = service(source);

        source.clear();

        assertThat(service.generateBatch()).extracting(SensorReading::sensorId).containsExactly("s1");
    }

    private SimulateTrafficService service(List<SensorScenario> sensors) {
        return new SimulateTrafficService(sensors, clock, () -> new UUID(0, sequence.incrementAndGet()));
    }

    private SensorScenario sensor(String id, int... counts) {
        List<MeasurementSample> samples = new ArrayList<>();
        for (int count : counts) {
            samples.add(new MeasurementSample(count, new BigDecimal("12.50"), CongestionState.MODERADA));
        }
        return new SensorScenario(id, "north", samples);
    }

    private static final class MutableClock extends Clock {
        private Instant current = START;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
