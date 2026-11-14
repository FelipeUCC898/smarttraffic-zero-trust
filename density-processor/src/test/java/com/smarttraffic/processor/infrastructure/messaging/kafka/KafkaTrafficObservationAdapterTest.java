package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smarttraffic.processor.application.model.ProcessingResult;
import com.smarttraffic.processor.application.port.in.ProcessTrafficObservationUseCase;
import com.smarttraffic.processor.application.port.out.TrafficStatePublisher;
import com.smarttraffic.processor.application.port.out.ZoneTrafficStateStore;
import com.smarttraffic.processor.application.service.ProcessTrafficObservationService;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaTrafficObservationAdapterTest {
    private final RawTrafficObservationDecoder decoder = new RawTrafficObservationDecoder(RawTrafficObservationDecoderTest.mapper());
    private final ProcessTrafficObservationUseCase useCase = mock(ProcessTrafficObservationUseCase.class);
    private final KafkaTrafficObservationAdapter adapter = new KafkaTrafficObservationAdapter(decoder, useCase);

    @Test
    void passesAValidatedObservationToTheUseCaseAndPreservesItsResult() {
        TrafficObservation observation = decoder.decode("sensor-1", bytes());
        ProcessingResult result = ProcessingResult.ignored(observation.eventId());
        when(useCase.process(observation)).thenReturn(result);

        assertThat(adapter.process("sensor-1", bytes())).isSameAs(result);

        verify(useCase).process(observation);
    }

    @Test
    void neverInvokesTheUseCaseForInvalidInput() {
        assertThatThrownBy(() -> adapter.process("other", bytes())).isInstanceOf(InvalidTrafficObservationException.class);
        assertThatThrownBy(() -> adapter.process("sensor-1", null)).isInstanceOf(InvalidTrafficObservationException.class);

        verifyNoInteractions(useCase);
    }

    @Test
    void neverClassifiesUseCaseFailuresAsInvalidMessages() {
        IllegalArgumentException failure = new IllegalArgumentException("Fallo de política de cálculo de prueba");
        when(useCase.process(any())).thenThrow(failure);

        assertThatThrownBy(() -> adapter.process("sensor-1", bytes())).isSameAs(failure)
                .isNotInstanceOf(InvalidTrafficObservationException.class);
    }

    @Test
    void rejectsNullOrUncorrelatedUseCaseResults() {
        when(useCase.process(any())).thenReturn(null).thenReturn(ProcessingResult.ignored(new UUID(0, 99)));

        assertThatThrownBy(() -> adapter.process("sensor-1", bytes())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.process("sensor-1", bytes())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runsJsonThroughTheRealApplicationAndDomainWithoutBrokers() {
        ZoneTrafficStateStore store = mock(ZoneTrafficStateStore.class);
        TrafficStatePublisher publisher = mock(TrafficStatePublisher.class);
        // Estrategia y umbrales artificiales, solo para esta prueba de colaboración.
        ZoneTrafficAggregate confirmed = new ZoneTrafficAggregate("north",
                observation -> BigDecimal.valueOf(observation.vehicleCount()),
                new CongestionThresholds(BigDecimal.TEN, new BigDecimal("20")));
        when(store.getOrCreate("north")).thenReturn(confirmed);
        KafkaTrafficObservationAdapter entry = new KafkaTrafficObservationAdapter(decoder,
                new ProcessTrafficObservationService(store, publisher));

        ProcessingResult result = entry.process("sensor-1", bytes());

        assertThat(result.outcome()).isEqualTo(ProcessingResult.Outcome.PROCESSED);
        assertThat(result.state()).hasValueSatisfying(state -> {
            assertThat(state.density()).isEqualByComparingTo("42");
            assertThat(state.congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
        });
        assertThat(confirmed.currentState()).isEmpty();
        verify(publisher).publish(result.state().orElseThrow());
        verify(store).save(eq("north"), any(ZoneTrafficAggregate.class));
    }

    @Test
    void preservesPublicationFailureAcrossTheRawEntryAndDoesNotSave() {
        ZoneTrafficStateStore store = mock(ZoneTrafficStateStore.class);
        TrafficStatePublisher publisher = mock(TrafficStatePublisher.class);
        ZoneTrafficAggregate confirmed = new ZoneTrafficAggregate("north", observation -> BigDecimal.ONE,
                new CongestionThresholds(BigDecimal.TEN, new BigDecimal("20")));
        when(store.getOrCreate("north")).thenReturn(confirmed);
        IllegalStateException failure = new IllegalStateException("Publicación de prueba fallida");
        doThrow(failure).when(publisher).publish(any(ZoneTrafficState.class));
        KafkaTrafficObservationAdapter entry = new KafkaTrafficObservationAdapter(decoder,
                new ProcessTrafficObservationService(store, publisher));

        assertThatThrownBy(() -> entry.process("sensor-1", bytes())).isSameAs(failure);

        verify(store, never()).save(any(), any());
        assertThat(confirmed.currentState()).isEmpty();
    }

    @Test
    void requiresBothCollaboratorsWithoutChangingDomainConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaTrafficObservationAdapter(null, useCase));
        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaTrafficObservationAdapter(decoder, null));
    }

    private byte[] bytes() {
        return RawTrafficObservationDecoderTest.bytes(RawTrafficObservationDecoderTest.JSON);
    }
}
