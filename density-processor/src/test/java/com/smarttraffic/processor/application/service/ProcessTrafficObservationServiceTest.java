package com.smarttraffic.processor.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.smarttraffic.processor.application.model.ProcessingResult;
import com.smarttraffic.processor.application.model.ProcessingResult.Outcome;
import com.smarttraffic.processor.application.port.out.TrafficStatePublisher;
import com.smarttraffic.processor.application.port.out.ZoneTrafficStateStore;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessTrafficObservationServiceTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    // Fórmula y umbrales artificiales de prueba, sin configuración productiva.
    private static final CongestionThresholds THRESHOLDS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));
    private static final DensityCalculationStrategy STRATEGY =
            observation -> BigDecimal.valueOf(observation.vehicleCount());

    @Mock
    private ZoneTrafficStateStore stateStore;
    @Mock
    private TrafficStatePublisher publisher;
    @InjectMocks
    private ProcessTrafficObservationService service;
    @Captor
    private ArgumentCaptor<ZoneTrafficAggregate> savedAggregate;

    @Test
    void processesACopyPublishesThenSavesWithoutMutatingTheConfirmedAggregate() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        ZoneTrafficState previous = confirmed.recordLatestObservation(observation(1, START, 5)).orElseThrow();
        TrafficObservation next = observation(2, START.plusSeconds(1), 25);
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);

        ProcessingResult result = service.process(next);

        assertThat(result.eventId()).isEqualTo(next.eventId());
        assertThat(result.outcome()).isEqualTo(Outcome.PROCESSED);
        ZoneTrafficState state = result.state().orElseThrow();
        assertThat(state.density()).isEqualByComparingTo("25");
        assertThat(state.congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
        assertThat(confirmed.currentState()).contains(previous);
        var order = inOrder(stateStore, publisher);
        order.verify(stateStore).getOrCreate("north");
        order.verify(publisher).publish(state);
        order.verify(stateStore).save(eq("north"), savedAggregate.capture());
        assertThat(savedAggregate.getValue()).isNotSameAs(confirmed);
        assertThat(savedAggregate.getValue().currentState()).contains(state);
        verifyNoMoreInteractions(stateStore, publisher);
    }

    @Test
    void ignoresAReplayWithoutPublishingOrSaving() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        TrafficObservation last = observation(1, START, 5);
        ZoneTrafficState previous = confirmed.recordLatestObservation(last).orElseThrow();
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);

        ProcessingResult result = service.process(last);

        assertThat(result).isEqualTo(ProcessingResult.ignored(last.eventId()));
        assertThat(confirmed.currentState()).contains(previous);
        verify(stateStore).getOrCreate("north");
        verifyNoMoreInteractions(stateStore);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void ignoresOldOrEqualTimestampsWithoutSideEffects(long seconds) {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        ZoneTrafficState previous = confirmed.recordLatestObservation(observation(1, START, 5)).orElseThrow();
        TrafficObservation next = observation(2, START.plusSeconds(seconds), 25);
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);

        ProcessingResult result = service.process(next);

        assertThat(result).isEqualTo(ProcessingResult.ignored(next.eventId()));
        assertThat(confirmed.currentState()).contains(previous);
        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsAMissingObservationBeforeUsingPorts() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.process(null))
                .withMessageContaining("observation");

        verifyNoInteractions(stateStore, publisher);
    }

    @Test
    void propagatesAReadFailureWithoutPublishingOrSaving() {
        IllegalStateException failure = new IllegalStateException("Fallo de lectura de prueba");
        when(stateStore.getOrCreate("north")).thenThrow(failure);

        assertThatThrownBy(() -> service.process(observation(1, START, 5))).isSameAs(failure);

        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsANullAggregateReturnedByTheStore() {
        when(stateStore.getOrCreate("north")).thenReturn(null);

        assertThatThrownBy(() -> service.process(observation(1, START, 5)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("agregado nulo");

        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsAnAggregateFromAnotherZoneWithoutSideEffects() {
        ZoneTrafficAggregate wrongZone = new ZoneTrafficAggregate("south", STRATEGY, THRESHOLDS);
        when(stateStore.getOrCreate("north")).thenReturn(wrongZone);

        assertThatIllegalArgumentException().isThrownBy(() -> service.process(observation(1, START, 5)))
                .withMessageContaining("zoneId");

        assertThat(wrongZone.currentState()).isEmpty();
        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void propagatesCalculationFailureWithoutChangingTheConfirmedState() {
        IllegalStateException failure = new IllegalStateException("Fallo de cálculo de prueba");
        ZoneTrafficAggregate confirmed = aggregate(observation -> {
            if (observation.vehicleCount() == 99) {
                throw failure;
            }
            return STRATEGY.calculate(observation);
        });
        ZoneTrafficState previous = confirmed.recordLatestObservation(observation(1, START, 5)).orElseThrow();
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);

        assertThatThrownBy(() -> service.process(observation(2, START.plusSeconds(1), 99))).isSameAs(failure);

        assertThat(confirmed.currentState()).contains(previous);
        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @MethodSource("invalidDensities")
    void rejectsInvalidCalculationResultsBeforePublishing(BigDecimal invalidDensity) {
        ZoneTrafficAggregate confirmed = aggregate(ignored -> invalidDensity);
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);

        assertThatIllegalArgumentException().isThrownBy(() -> service.process(observation(1, START, 5)))
                .withMessageContaining("density");

        assertThat(confirmed.currentState()).isEmpty();
        verify(stateStore, never()).save(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void propagatesPublicationFailureWithoutSavingOrChangingTheConfirmedState() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        ZoneTrafficState previous = confirmed.recordLatestObservation(observation(1, START, 5)).orElseThrow();
        IllegalStateException failure = new IllegalStateException("Publicación fallida o incierta de prueba");
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);
        doThrow(failure).when(publisher).publish(any(ZoneTrafficState.class));

        assertThatThrownBy(() -> service.process(observation(2, START.plusSeconds(1), 25))).isSameAs(failure);

        assertThat(confirmed.currentState()).contains(previous);
        verify(stateStore, never()).save(any(), any());
    }

    @Test
    void canRetryTheSameObservationAfterPublicationFailure() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        TrafficObservation next = observation(1, START, 25);
        IllegalStateException failure = new IllegalStateException("Primer intento fallido de prueba");
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);
        doThrow(failure).doNothing().when(publisher).publish(any(ZoneTrafficState.class));

        assertThatThrownBy(() -> service.process(next)).isSameAs(failure);
        assertThat(confirmed.currentState()).isEmpty();
        ProcessingResult retried = service.process(next);

        assertThat(retried.outcome()).isEqualTo(Outcome.PROCESSED);
        verify(publisher, times(2)).publish(retried.state().orElseThrow());
        verify(stateStore).save(eq("north"), savedAggregate.capture());
        assertThat(savedAggregate.getValue().currentState()).isEqualTo(retried.state());
        assertThat(confirmed.currentState()).isEmpty();
    }

    @Test
    void propagatesSaveFailureAfterPublishingWithoutReturningSuccess() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        IllegalStateException failure = new IllegalStateException("Fallo de guardado de prueba");
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);
        doThrow(failure).when(stateStore).save(eq("north"), any(ZoneTrafficAggregate.class));

        assertThatThrownBy(() -> service.process(observation(1, START, 25))).isSameAs(failure);

        var order = inOrder(stateStore, publisher);
        order.verify(stateStore).getOrCreate("north");
        order.verify(publisher).publish(any(ZoneTrafficState.class));
        order.verify(stateStore).save(eq("north"), any(ZoneTrafficAggregate.class));
        assertThat(confirmed.currentState()).isEmpty();
    }

    @Test
    void aRetryCanRepublishWhenSaveFailedWithoutCommitting() {
        ZoneTrafficAggregate confirmed = aggregate(STRATEGY);
        TrafficObservation next = observation(1, START, 25);
        IllegalStateException failure = new IllegalStateException("Guardado no confirmado de prueba");
        when(stateStore.getOrCreate("north")).thenReturn(confirmed);
        doThrow(failure).doNothing().when(stateStore).save(eq("north"), any(ZoneTrafficAggregate.class));

        assertThatThrownBy(() -> service.process(next)).isSameAs(failure);
        ProcessingResult retried = service.process(next);

        assertThat(retried.outcome()).isEqualTo(Outcome.PROCESSED);
        verify(publisher, times(2)).publish(retried.state().orElseThrow());
        verify(stateStore, times(2)).save(eq("north"), any(ZoneTrafficAggregate.class));
    }

    @Test
    void aSuccessfullyCommittedObservationIsIgnoredOnReplay() {
        AtomicReference<ZoneTrafficAggregate> stored = new AtomicReference<>(aggregate(STRATEGY));
        TrafficObservation next = observation(1, START, 25);
        when(stateStore.getOrCreate("north")).thenAnswer(ignored -> stored.get());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(stateStore).save(eq("north"), any(ZoneTrafficAggregate.class));

        ProcessingResult first = service.process(next);
        ProcessingResult replay = service.process(next);

        assertThat(first.outcome()).isEqualTo(Outcome.PROCESSED);
        assertThat(replay).isEqualTo(ProcessingResult.ignored(next.eventId()));
        verify(publisher).publish(first.state().orElseThrow());
        verify(stateStore).save(eq("north"), any(ZoneTrafficAggregate.class));
    }

    @Test
    void requiresBothPortsAtConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessTrafficObservationService(null, publisher)).withMessageContaining("stateStore");
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProcessTrafficObservationService(stateStore, null)).withMessageContaining("publisher");
    }

    private static Stream<BigDecimal> invalidDensities() {
        return Stream.of(null, new BigDecimal("-0.01"));
    }

    private ZoneTrafficAggregate aggregate(DensityCalculationStrategy strategy) {
        return new ZoneTrafficAggregate("north", strategy, THRESHOLDS);
    }

    private TrafficObservation observation(long id, Instant measuredAt, int count) {
        return new TrafficObservation(new UUID(0, id), "sensor-1", "north", measuredAt,
                count, BigDecimal.ONE, CongestionState.DESPEJADA);
    }
}
