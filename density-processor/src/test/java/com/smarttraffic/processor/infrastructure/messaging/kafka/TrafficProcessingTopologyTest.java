package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrafficProcessingTopologyTest {
    private static final String SOURCE = "raw_traffic_data";
    private static final String STORE = "test-only-zones";
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final CongestionThresholds LIMITS =
            new CongestionThresholds(new BigDecimal("10"), new BigDecimal("20"));
    // Algoritmo artificial exclusivo de tests; no es una fórmula productiva.
    private static final DensityCalculationStrategy STRATEGY = reading -> BigDecimal.valueOf(reading.vehicleCount());
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @TempDir Path stateDirectory;

    @Test
    void materializesLatestObservationsFromDifferentSensorsUnderOneZone() throws Exception {
        try (var driver = driver(STRATEGY)) {
            pipe(driver, observation(1, "sensor-a", "north", TIME, 12));
            var next = observation(2, "sensor-b", "north", TIME.plusSeconds(1), 20);
            pipe(driver, next);
            ZoneTrafficSnapshot state = state(driver, "north");
            assertThat(state.latestObservation()).isEqualTo(next);
            assertThat(state.latestState().density()).isEqualByComparingTo("20");
            assertThat(state.latestState().congestionState()).isEqualTo(CongestionState.CONGESTIONADA);
            assertThat(state(driver, "sensor-a")).isNull();
        }
    }

    @Test
    void maintainsIndependentZonesEvenForTheSameSensorKey() throws Exception {
        try (var driver = driver(STRATEGY)) {
            pipe(driver, observation(1, "sensor-a", "north", TIME, 1));
            pipe(driver, observation(2, "sensor-a", "south", TIME, 20));
            assertThat(state(driver, "north").latestState().density()).isEqualByComparingTo("1");
            assertThat(state(driver, "south").latestState().density()).isEqualByComparingTo("20");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-key", "null-key", "null-body", "invalid-json"})
    void rejectsInvalidSourceRecordsBeforeUpdatingState(String variant) throws Exception {
        try (var driver = driver(STRATEGY)) {
            var input = driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer());
            byte[] bytes = mapper.writeValueAsBytes(observation(1, "sensor-a", "north", TIME, 12));
            String key = variant.equals("null-key") ? null : variant.equals("wrong-key") ? "other" : "sensor-a";
            if (variant.equals("null-body")) bytes = null;
            if (variant.equals("invalid-json")) bytes = new byte[] {'{'};
            byte[] payload = bytes;
            assertThatThrownBy(() -> input.pipeInput(key, payload))
                    .hasRootCauseInstanceOf(InvalidTrafficObservationException.class);
            assertThat(state(driver, "north")).isNull();
        }
    }

    @Test
    void keepsStateForReplayOldAndEqualTimeObservations() throws Exception {
        try (var driver = driver(STRATEGY)) {
            var accepted = observation(1, "sensor-a", "north", TIME, 12);
            pipe(driver, accepted);
            var confirmed = state(driver, "north");
            pipe(driver, accepted);
            pipe(driver, observation(2, "sensor-b", "north", TIME.minusSeconds(1), 20));
            pipe(driver, observation(3, "sensor-b", "north", TIME, 20));
            assertThat(state(driver, "north")).isEqualTo(confirmed);
        }
    }

    @Test
    void rejectsConflictingLastEventWithoutReplacingConfirmedState() throws Exception {
        try (var driver = driver(STRATEGY)) {
            pipe(driver, observation(1, "sensor-a", "north", TIME, 12));
            var confirmed = state(driver, "north");
            assertThatThrownBy(() -> pipe(driver, observation(1, "sensor-a", "north", TIME.plusSeconds(1), 20)))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(state(driver, "north")).isEqualTo(confirmed);
        }
    }

    @Test
    void preservesConfirmedStateWhenCalculationFails() throws Exception {
        try (var driver = driver(reading -> {
            if (reading.vehicleCount() == 20) throw new IllegalStateException("Cálculo no disponible");
            return STRATEGY.calculate(reading);
        })) {
            pipe(driver, observation(1, "sensor-a", "north", TIME, 12));
            var confirmed = state(driver, "north");
            assertThatThrownBy(() -> pipe(driver, observation(2, "sensor-a", "north", TIME.plusSeconds(1), 20)))
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(state(driver, "north")).isEqualTo(confirmed);
        }
    }

    @Test
    void continuesFromAPreloadedSnapshotWithoutClaimingBrokerRecovery() throws Exception {
        try (var driver = driver(STRATEGY)) {
            var aggregate = new ZoneTrafficAggregate("north", STRATEGY,
                    new CongestionThresholds(new BigDecimal("10.00"), new BigDecimal("20.00")));
            var accepted = observation(1, "sensor-a", "north", TIME, 12);
            aggregate.recordLatestObservation(accepted);
            driver.<String, ZoneTrafficSnapshot>getTimestampedKeyValueStore(STORE)
                    .put("north", ValueAndTimestamp.make(aggregate.snapshot(), 0));
            pipe(driver, accepted);
            assertThat(state(driver, "north")).isEqualTo(aggregate.snapshot());
            pipe(driver, observation(2, "sensor-b", "north", TIME.plusSeconds(1), 20));
            assertThat(state(driver, "north").latestState().density()).isEqualByComparingTo("20");
        }
    }

    @Test
    void refusesUnmigratedThresholdChanges() throws Exception {
        try (var driver = driver(STRATEGY)) {
            var other = new ZoneTrafficAggregate("north", STRATEGY,
                    new CongestionThresholds(BigDecimal.ONE, new BigDecimal("2")));
            other.recordLatestObservation(observation(1, "sensor-a", "north", TIME, 12));
            driver.<String, ZoneTrafficSnapshot>getTimestampedKeyValueStore(STORE)
                    .put("north", ValueAndTimestamp.make(other.snapshot(), 0));
            assertThatThrownBy(() -> pipe(driver, observation(2, "sensor-a", "north", TIME.plusSeconds(1), 20)))
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(state(driver, "north")).isEqualTo(other.snapshot());
        }
    }

    @Test
    void requiresExplicitCollaboratorsAndResourceNames() {
        var decoder = new RawTrafficObservationDecoder(mapper);
        assertThatIllegalArgumentException().isThrownBy(() -> new TrafficProcessingTopology(null, STRATEGY, LIMITS));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrafficProcessingTopology(decoder, null, LIMITS));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrafficProcessingTopology(decoder, STRATEGY, null));
        var topology = new TrafficProcessingTopology(decoder, STRATEGY, LIMITS);
        assertThatIllegalArgumentException().isThrownBy(() -> topology.build(null, SOURCE, STORE));
        assertThatIllegalArgumentException().isThrownBy(() -> topology.build(new StreamsBuilder(), " ", STORE));
        assertThatIllegalArgumentException().isThrownBy(() -> topology.build(new StreamsBuilder(), SOURCE, "../state"));
    }

    @Test
    void buildsARepartitionedGraphWithoutStartingAClient() {
        var builder = new StreamsBuilder();
        new TrafficProcessingTopology(new RawTrafficObservationDecoder(mapper), STRATEGY, LIMITS)
                .build(builder, SOURCE, STORE);
        assertThat(builder.build().describe().toString()).contains("repartition", STORE, SOURCE);
    }

    private TopologyTestDriver driver(DensityCalculationStrategy strategy) {
        var builder = new StreamsBuilder();
        new TrafficProcessingTopology(new RawTrafficObservationDecoder(mapper), strategy, LIMITS)
                .build(builder, SOURCE, STORE);
        var config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-only-density");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "unused.invalid:9092");
        config.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        return new TopologyTestDriver(builder.build(), config);
    }

    private void pipe(TopologyTestDriver driver, TrafficObservation reading) throws Exception {
        driver.createInputTopic(SOURCE, Serdes.String().serializer(), Serdes.ByteArray().serializer())
                .pipeInput(reading.sensorId(), mapper.writeValueAsBytes(reading));
    }

    private static ZoneTrafficSnapshot state(TopologyTestDriver driver, String zone) {
        return driver.<String, ZoneTrafficSnapshot>getKeyValueStore(STORE).get(zone);
    }

    private static TrafficObservation observation(long id, String sensor, String zone, Instant time, int count) {
        return new TrafficObservation(new UUID(0, id), sensor, zone, time, count,
                new BigDecimal("35.50"), CongestionState.MODERADA);
    }
}
