package com.smarttraffic.processor.infrastructure.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.application.port.out.ZoneTrafficStateStore;
import com.smarttraffic.processor.application.service.ProcessTrafficObservationService;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitTrafficStatePublisherTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final ConnectionFactory factory = mock(ConnectionFactory.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void configureTemplateWithoutConnections() {
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.isPublisherConfirms()).thenReturn(true);
        when(factory.isPublisherReturns()).thenReturn(true);
        when(template.isMandatoryFor(any(Message.class))).thenReturn(true);
    }

    @Test
    void publishesTheFiveContractFieldsAsPersistentUtf8AndWaitsForAck() throws Exception {
        ack();
        publisher().publish(state(null));
        var capture = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq("traffic_updates"), eq(""), capture.capture(), any(CorrelationData.class));
        Message message = capture.getValue();
        var json = mapper.readTree(message.getBody());
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("zoneId").asText()).isEqualTo("zona-ñ");
        assertThat(json.get("measuredAt").asText()).isEqualTo("2026-01-01T00:00:00.123456789Z");
        assertThat(json.get("congestionState").asText()).isEqualTo("MODERADA");
        assertThat(json.get("version").isNull()).isTrue();
        assertThat(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("12.500000000000000000001").doesNotContain("formatVersion", "thresholds", "@class");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        verify(factory, never()).createConnection();
    }

    @Test
    void preservesTheVersionWithoutDefiningItsMeaning() throws Exception {
        ack();
        publisher().publish(state("opaque-version"));
        var capture = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq("traffic_updates"), eq(""), capture.capture(), any(CorrelationData.class));
        assertThat(mapper.readTree(capture.getValue().getBody()).get("version").asText()).isEqualTo("opaque-version");
    }

    @Test
    void createsIndependentCorrelationForEveryAttempt() {
        List<CorrelationData> attempts = new ArrayList<>();
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            attempts.add(correlation);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        var publisher = publisher();
        publisher.publish(state(null));
        publisher.publish(state(null));
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).getId()).isNotBlank().isNotEqualTo(attempts.get(1).getId());
    }

    @Test
    void propagatesNackWithoutExposingTheBrokerReason() {
        doAnswer(call -> {
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(false, "private-broker-info"));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher().publish(state(null)))
                .isInstanceOf(TrafficStatePublicationException.class).hasMessage("RabbitMQ no confirmó la publicación");
    }

    @Test
    void treatsReturnedMessagesAsFailureEvenWhenTheBrokerAcks() {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "traffic_updates", ""));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher().publish(state(null)))
                .isInstanceOf(TrafficStatePublicationException.class).hasMessage("RabbitMQ devolvió el estado sin ruta");
    }

    @Test
    void timesOutWithoutClaimingTheMessageWasNotDelivered() {
        assertThatThrownBy(() -> new RabbitTrafficStatePublisher(template, 1).publish(state(null)))
                .isInstanceOf(TrafficStatePublicationException.class).hasMessageContaining("entrega incierta");
    }

    @Test
    void preservesInterruptionAndReportsUncertainDelivery() {
        doAnswer(call -> { Thread.currentThread().interrupt(); return null; })
                .when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        try {
            assertThatThrownBy(() -> publisher().publish(state(null)))
                    .isInstanceOf(TrafficStatePublicationException.class).hasMessageContaining("interrumpida");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void sanitizesAnExceptionDuringSend() {
        doAnswer(call -> { throw new AmqpException("private-connection-details"); })
                .when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher().publish(state(null)))
                .isInstanceOf(TrafficStatePublicationException.class)
                .hasMessage("Falló el envío o la confirmación del estado").hasNoCause();
    }

    @Test
    void reportsExceptionalConfirmationWithoutExposingItsCause() {
        doAnswer(call -> {
            call.<CorrelationData>getArgument(3).getFuture().completeExceptionally(new IllegalStateException("private-info"));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher().publish(state(null)))
                .isInstanceOf(TrafficStatePublicationException.class).hasNoCause();
    }

    @Test
    void rejectsNullBeforeSending() {
        assertThatIllegalArgumentException().isThrownBy(() -> publisher().publish(null));
        verify(template, never()).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void requiresMandatoryPublicationBeforeSending() {
        when(template.isMandatoryFor(any(Message.class))).thenReturn(false);
        assertThatThrownBy(() -> publisher().publish(state(null))).isInstanceOf(IllegalStateException.class);
        verify(template, never()).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-factory", "missing-confirms", "missing-returns", "transactional", "alternate-factory"})
    void rejectsInsufficientConnectionConfiguration(String variant) {
        if (variant.equals("missing-factory")) when(template.getConnectionFactory()).thenReturn(null);
        if (variant.equals("missing-confirms")) when(factory.isPublisherConfirms()).thenReturn(false);
        if (variant.equals("missing-returns")) when(factory.isPublisherReturns()).thenReturn(false);
        if (variant.equals("transactional")) when(template.isChannelTransacted()).thenReturn(true);
        if (variant.equals("alternate-factory")) when(template.isUsePublisherConnection()).thenReturn(true);
        assertThatThrownBy(this::publisher).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechecksConnectionRequirementsBeforeEachPublication() {
        var publisher = publisher();
        when(factory.isPublisherReturns()).thenReturn(false);
        assertThatThrownBy(() -> publisher.publish(state(null))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresTemplateAndPositiveTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RabbitTrafficStatePublisher(null, 100));
        assertThatIllegalArgumentException().isThrownBy(() -> new RabbitTrafficStatePublisher(template, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RabbitTrafficStatePublisher(template, -1));
    }

    private RabbitTrafficStatePublisher publisher() {
        return new RabbitTrafficStatePublisher(template, 1000);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void applicationSavesOnlyAfterTheRealAdapterAcceptsTheConfirmation(boolean acknowledged) {
        var store = mock(ZoneTrafficStateStore.class);
        var aggregate = new ZoneTrafficAggregate("zona-ñ", observation -> BigDecimal.valueOf(observation.vehicleCount()),
                new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20")));
        when(store.getOrCreate("zona-ñ")).thenReturn(aggregate);
        doAnswer(call -> {
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(acknowledged, null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
        var service = new ProcessTrafficObservationService(store, publisher());
        var reading = new TrafficObservation(new UUID(0, 1), "sensor-1", "zona-ñ", Instant.parse("2026-01-01T00:00:00Z"),
                12, BigDecimal.TEN, CongestionState.MODERADA);
        if (acknowledged) {
            assertThat(service.process(reading).state()).isPresent();
            verify(store).save(eq("zona-ñ"), any(ZoneTrafficAggregate.class));
        } else {
            assertThatThrownBy(() -> service.process(reading)).isInstanceOf(TrafficStatePublicationException.class);
            verify(store, never()).save(any(String.class), any(ZoneTrafficAggregate.class));
        }
        assertThat(aggregate.currentState()).isEmpty();
    }

    private void ack() {
        doAnswer(call -> {
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
    }

    private static ZoneTrafficState state(String version) {
        return new ZoneTrafficState("zona-ñ", Instant.parse("2026-01-01T00:00:00.123456789Z"),
                new BigDecimal("12.500000000000000000001"), CongestionState.MODERADA, version);
    }
}
