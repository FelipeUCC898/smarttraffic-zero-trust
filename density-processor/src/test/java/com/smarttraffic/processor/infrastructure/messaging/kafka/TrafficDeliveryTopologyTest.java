package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.processor.domain.model.*;
import com.smarttraffic.processor.domain.strategy.VehiclesPerKilometerDensityStrategy;
import com.smarttraffic.processor.infrastructure.messaging.rabbitmq.RabbitTrafficStatePublisher;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class TrafficDeliveryTopologyTest {
    private static final String SOURCE = "raw_traffic_data", ZONES = "test-zones", PENDING = "test-pending";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final AtomicBoolean ack = new AtomicBoolean(true);
    private final List<Message> sent = new ArrayList<>();
    @TempDir Path directory;

    @BeforeEach void configureMockConnectionOnly() {
        var factory = mock(ConnectionFactory.class);
        when(template.getConnectionFactory()).thenReturn(factory);
        when(factory.isPublisherConfirms()).thenReturn(true);
        when(factory.isPublisherReturns()).thenReturn(true);
        when(template.isMandatoryFor(any())).thenReturn(true);
        doAnswer(call -> {
            sent.add(call.getArgument(2));
            call.<CorrelationData>getArgument(3).getFuture().complete(new CorrelationData.Confirm(ack.get(), null));
            return null;
        }).when(template).send(eq("traffic_updates"), eq(""), any(Message.class), any(CorrelationData.class));
    }
    @Test void oneObservationIsCalculatedQueuedAndPublishedWithoutManualRabbitJson() throws Exception {
        try (var driver = driver(10)) {
            pipe(driver, reading(1));
            assertThat(driver.<String, ZoneTrafficSnapshot>getKeyValueStore(ZONES).get("north").latestState().density()).isEqualByComparingTo("21");
            assertThat(pending(driver, 1)).isNotNull(); assertThat(sent).isEmpty();
            driver.advanceWallClockTime(Duration.ofSeconds(1));
            assertThat(sent).hasSize(1); assertThat(pending(driver, 1)).isNull();
            var json = mapper.readTree(sent.get(0).getBody());
            assertThat(json.size()).isEqualTo(5);
            assertThat(json.get("density").decimalValue()).isEqualByComparingTo("21");
            assertThat(json.get("congestionState").asText()).isEqualTo("CONGESTIONADA");
            assertThat(json.get("zoneId").asText()).isEqualTo("north");
        }
    }
    @Test void nackRetainsPendingAndRecoversWithoutAnotherKafkaObservation() throws Exception {
        try (var driver = driver(10)) {
            ack.set(false); pipe(driver, reading(1)); driver.advanceWallClockTime(Duration.ofSeconds(1));
            assertThat(pending(driver, 1)).isNotNull();
            ack.set(true); driver.advanceWallClockTime(Duration.ofSeconds(1));
            assertThat(pending(driver, 1)).isNull(); assertThat(sent).hasSize(2);
        }
    }
    @Test void attemptsPreviouslyStoredPendingEvenWithoutNewInput() {
        try (var driver = driver(10)) {
            var observation = reading(1);
            var snapshot = new ZoneTrafficSnapshot("north", new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20")),
                    observation, new ZoneTrafficState("north", observation.measuredAt(), new BigDecimal("21"), CongestionState.CONGESTIONADA, null));
            driver.<String, ZoneTrafficSnapshot>getKeyValueStore(PENDING).put(observation.eventId().toString(), snapshot);
            driver.advanceWallClockTime(Duration.ofSeconds(1));
            assertThat(pending(driver, 1)).isNull(); assertThat(sent).hasSize(1);
        }
    }
    @Test void batchLimitLeavesRemainingMessagesForNextTick() throws Exception {
        try (var driver = driver(1)) {
            pipe(driver, reading(1)); pipe(driver, reading(2));
            driver.advanceWallClockTime(Duration.ofSeconds(1)); assertThat(sent).hasSize(1);
            driver.advanceWallClockTime(Duration.ofSeconds(1)); assertThat(sent).hasSize(2);
            assertThat(pending(driver, 1)).isNull(); assertThat(pending(driver, 2)).isNull();
        }
    }
    @Test void rejectsInvalidInputBeforeQueuingAnything() {
        try (var driver = driver(10)) {
            assertThatThrownBy(() -> driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer())
                    .pipeInput("sensor-1", new byte[] {'{'})).hasRootCauseInstanceOf(InvalidTrafficObservationException.class);
            assertThat(pending(driver, 1)).isNull(); assertThat(sent).isEmpty();
        }
    }
    private TopologyTestDriver driver(int batch) {
        var strategy = new VehiclesPerKilometerDensityStrategy(Map.of("north", new BigDecimal("2")));
        var processing = new TrafficProcessingTopology(new RawTrafficObservationDecoder(mapper), strategy,
                new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20")));
        var topology = new TrafficDeliveryTopology().build(new StreamsBuilder(), processing,
                new RabbitTrafficStatePublisher(template, 1000), SOURCE, ZONES, PENDING, Duration.ofSeconds(1), batch);
        var properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-connected");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused.invalid:9092");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, directory.toString());
        return new TopologyTestDriver(topology, properties);
    }
    private ZoneTrafficSnapshot pending(TopologyTestDriver driver, int id) {
        return driver.<String, ZoneTrafficSnapshot>getKeyValueStore(PENDING).get(new UUID(0, id).toString());
    }
    private void pipe(TopologyTestDriver driver, TrafficObservation observation) throws Exception {
        driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer())
                .pipeInput(observation.sensorId(), mapper.writeValueAsBytes(observation));
    }
    private TrafficObservation reading(int id) {
        return new TrafficObservation(new UUID(0, id), "sensor-1", "north", Instant.parse("2026-01-01T00:00:00Z").plusSeconds(id),
                42, new BigDecimal("35.50"), CongestionState.MODERADA);
    }
}
