package com.smarttraffic.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smarttraffic.simulator.application.port.out.TrafficObservationPublisher;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.domain.model.CongestionState;
import com.smarttraffic.simulator.domain.model.SensorReading;
import com.smarttraffic.simulator.infrastructure.scheduling.KafkaSimulationScheduler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaSchedulerTest {
    @Test
    void retainsFailedReadingAndDoesNotRepeatPreviouslyConfirmedSensors() {
        SimulateTrafficService simulator = mock(SimulateTrafficService.class);
        TrafficObservationPublisher publisher = mock(TrafficObservationPublisher.class);
        SensorReading first = reading(1, "sensor-1");
        SensorReading second = reading(2, "sensor-2");
        when(simulator.generateBatch()).thenReturn(List.of(first, second));
        IllegalStateException failure = new IllegalStateException("Fallo de envío de prueba");
        doThrow(failure).doNothing().when(publisher).publish(second);
        KafkaSimulationScheduler scheduler = new KafkaSimulationScheduler(simulator, publisher);

        assertThatThrownBy(scheduler::publishNextBatch).isSameAs(failure);
        scheduler.publishNextBatch();

        verify(simulator).generateBatch();
        verify(publisher).publish(first);
        verify(publisher, times(2)).publish(second);
        var order = inOrder(publisher);
        order.verify(publisher).publish(first);
        order.verify(publisher, times(2)).publish(second);
    }

    @Test
    void generatesANewBatchOnlyAfterThePreviousOneCompletes() {
        SimulateTrafficService simulator = mock(SimulateTrafficService.class);
        TrafficObservationPublisher publisher = mock(TrafficObservationPublisher.class);
        when(simulator.generateBatch()).thenReturn(List.of(reading(1, "sensor-1"))).thenReturn(List.of());
        KafkaSimulationScheduler scheduler = new KafkaSimulationScheduler(simulator, publisher);

        scheduler.publishNextBatch();
        scheduler.publishNextBatch();

        verify(simulator, times(2)).generateBatch();
        verify(publisher).publish(reading(1, "sensor-1"));
    }

    @Test
    void generationFailuresDoNotPublishAnything() {
        SimulateTrafficService simulator = mock(SimulateTrafficService.class);
        TrafficObservationPublisher publisher = mock(TrafficObservationPublisher.class);
        when(simulator.generateBatch()).thenThrow(new IllegalStateException("Fallo de prueba"));

        assertThatThrownBy(() -> new KafkaSimulationScheduler(simulator, publisher).publishNextBatch())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(publisher);
    }

    private SensorReading reading(long id, String sensor) {
        return new SensorReading(new UUID(0, id), sensor, "north", Instant.parse("2026-01-01T00:00:00Z"),
                1, BigDecimal.ONE, CongestionState.DESPEJADA);
    }
}
