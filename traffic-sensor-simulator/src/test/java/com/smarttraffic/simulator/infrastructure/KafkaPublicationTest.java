package com.smarttraffic.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.simulator.domain.model.CongestionState;
import com.smarttraffic.simulator.domain.model.SensorReading;
import com.smarttraffic.simulator.infrastructure.messaging.kafka.KafkaTrafficObservationPublisher;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

class KafkaPublicationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, byte[]> kafka = mock(KafkaOperations.class);
    private final SensorReading reading = new SensorReading(new UUID(0, 1), "sensor-1", "north",
            Instant.parse("2026-01-01T00:00:00Z"), 5, BigDecimal.TEN, CongestionState.MODERADA);

    @Test
    void publishesJsonUsingTheContractTopicAndSensorKeyAfterAcknowledgement() throws Exception {
        when(kafka.send(eq("raw_traffic_data"), eq("sensor-1"), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publish(reading);

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(kafka).send(eq("raw_traffic_data"), eq("sensor-1"), body.capture());
        assertThat(mapper.readValue(body.getValue(), SensorReading.class)).isEqualTo(reading);
        assertThat(mapper.readTree(body.getValue()).size()).isEqualTo(7);
        assertThat(mapper.readTree(body.getValue()).get("measuredAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void rejectsMissingReadingsAndPropagatesAsynchronousFailures() {
        assertThatIllegalArgumentException().isThrownBy(() -> publisher().publish(null));
        verifyNoInteractions(kafka);
        IllegalStateException failure = new IllegalStateException("Fallo de broker de prueba");
        when(kafka.send(eq("raw_traffic_data"), eq("sensor-1"), any(byte[].class)))
                .thenReturn(CompletableFuture.failedFuture(failure));
        assertThatThrownBy(() -> publisher().publish(reading)).isInstanceOf(IllegalStateException.class)
                .hasRootCause(failure);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTimeoutIsAnErrorAndAnInterruptedWaitRestoresTheThreadFlag() throws Exception {
        CompletableFuture<SendResult<String, byte[]>> future = mock(CompletableFuture.class);
        when(kafka.send(eq("raw_traffic_data"), eq("sensor-1"), any(byte[].class))).thenReturn(future);
        when(future.get(1000, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException());
        assertThatThrownBy(() -> publisher().publish(reading)).isInstanceOf(IllegalStateException.class);
        doThrow(new InterruptedException()).when(future).get(1000, TimeUnit.MILLISECONDS);
        try {
            assertThatThrownBy(() -> publisher().publish(reading)).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private KafkaTrafficObservationPublisher publisher() {
        return new KafkaTrafficObservationPublisher(kafka, mapper, "raw_traffic_data", 1000);
    }
}
