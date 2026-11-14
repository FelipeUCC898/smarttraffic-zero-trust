package com.smarttraffic.processor.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import com.smarttraffic.processor.infrastructure.messaging.kafka.InvalidTrafficObservationException;
import com.smarttraffic.processor.infrastructure.messaging.kafka.RawTrafficObservationDecoder;
import com.smarttraffic.processor.infrastructure.messaging.kafka.TrafficProcessingTopology;
import com.smarttraffic.processor.infrastructure.messaging.rabbitmq.RabbitTrafficStatePublisher;
import com.smarttraffic.processor.infrastructure.messaging.rabbitmq.TrafficStatePublicationException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Composición exclusivamente de prueba: demuestra por qué foreach no es una
 * entrega durable. No se registra como bean ni como topología productiva.
 */
class TrafficPipelineLocalTest {
    private static final String SOURCE = "raw_traffic_data";
    private static final String STORE = "test-only-pipeline";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final DensityCalculationStrategy STRATEGY = reading -> BigDecimal.valueOf(reading.vehicleCount());
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final AtomicBoolean acknowledge = new AtomicBoolean(true);
    private final List<Message> sent = new ArrayList<>();
    @TempDir Path stateDirectory;

    @BeforeEach
    void simulateOnlyTheAmqpBoundary() {
        var factory = mock(ConnectionFactory.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.isPublisherConfirms()).thenReturn(true);
        when(factory.isPublisherReturns()).thenReturn(true);
        when(template.isMandatoryFor(any(Message.class))).thenReturn(true);
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            call.<CorrelationData>getArgument(3).getFuture()
                    .complete(new CorrelationData.Confirm(acknowledge.get(), null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void passesJsonThroughTheRealTopologyAndPublisherWithAnAmqpMock() throws Exception {
        try (var driver = driver(STRATEGY)) {
            pipe(driver, reading(1, "sensor-a", 12));
            pipe(driver, reading(2, "sensor-b", 20));
            assertThat(sent).hasSize(2);
            var output = mapper.readTree(sent.get(1).getBody());
            assertThat(output.size()).isEqualTo(5);
            assertThat(output.get("zoneId").asText()).isEqualTo("north");
            assertThat(output.get("density").decimalValue()).isEqualByComparingTo("20");
            assertThat(output.get("congestionState").asText()).isEqualTo("CONGESTIONADA");
            assertThat(output.get("measuredAt").asText()).isEqualTo("2026-01-01T00:00:02Z");
            assertThat(state(driver).latestObservation().sensorId()).isEqualTo("sensor-b");
        }
    }

    @Test
    void invalidJsonDoesNotUpdateStateOrReachThePublisher() {
        try (var driver = driver(STRATEGY)) {
            var input = driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer());
            assertThatThrownBy(() -> input.pipeInput("sensor-a", new byte[] {'['}))
                    .hasRootCauseInstanceOf(InvalidTrafficObservationException.class);
            assertThat(state(driver)).isNull();
            assertThat(sent).isEmpty();
            verify(template, never()).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        }
    }

    @Test
    void calculationFailureDoesNotPublishOrReplaceThePreviousSnapshot() throws Exception {
        try (var driver = driver(reading -> {
            if (reading.vehicleCount() == 20) throw new IllegalStateException("Cálculo no disponible");
            return STRATEGY.calculate(reading);
        })) {
            pipe(driver, reading(1, "sensor-a", 12));
            var confirmed = state(driver);
            assertThatThrownBy(() -> pipe(driver, reading(2, "sensor-b", 20)))
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(state(driver)).isEqualTo(confirmed);
            assertThat(sent).hasSize(1);
        }
    }

    @Test
    void demonstratesThatPublisherFailureDoesNotUndoTheKTableLocalUpdate() throws Exception {
        try (var driver = driver(STRATEGY)) {
            pipe(driver, reading(1, "sensor-a", 12));
            acknowledge.set(false);
            assertThatThrownBy(() -> pipe(driver, reading(2, "sensor-b", 20)))
                    .hasRootCauseInstanceOf(TrafficStatePublicationException.class);
            assertThat(state(driver).latestState().density()).isEqualByComparingTo("20");
            assertThat(sent).hasSize(2);
            // Estado local avanzado con entrega fallida: foreach no es una transacción entre brokers.
        }
    }

    @Test
    void demonstratesThatReplayedInputCanPublishTheSameStateAgain() throws Exception {
        try (var driver = driver(STRATEGY)) {
            var accepted = reading(1, "sensor-a", 12);
            pipe(driver, accepted);
            var confirmed = state(driver);
            pipe(driver, accepted);
            assertThat(state(driver)).isEqualTo(confirmed);
            assertThat(sent).hasSize(2);
            assertThat(sent.get(0).getBody()).isEqualTo(sent.get(1).getBody());
        }
    }

    private TopologyTestDriver driver(DensityCalculationStrategy strategy) {
        var builder = new StreamsBuilder();
        var table = new TrafficProcessingTopology(new RawTrafficObservationDecoder(mapper), strategy,
                new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20")))
                .build(builder, SOURCE, STORE);
        var publisher = new RabbitTrafficStatePublisher(template, 1000);
        // Solo test: conectar así permite observar los fallos; no se propone desplegar este puente.
        table.toStream().foreach((zone, snapshot) -> publisher.publish(snapshot.latestState()));
        var config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-only-pipeline");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused.invalid:9092");
        config.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        return new TopologyTestDriver(builder.build(), config);
    }

    private void pipe(TopologyTestDriver driver, TrafficObservation reading) throws Exception {
        driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer())
                .pipeInput(reading.sensorId(), mapper.writeValueAsBytes(reading));
    }

    private ZoneTrafficSnapshot state(TopologyTestDriver driver) {
        return driver.<String, ZoneTrafficSnapshot>getKeyValueStore(STORE).get("north");
    }

    private TrafficObservation reading(long id, String sensor, int count) {
        return new TrafficObservation(new UUID(0, id), sensor, "north", START.plusSeconds(id), count,
                new BigDecimal("35.50"), CongestionState.MODERADA);
    }
}
