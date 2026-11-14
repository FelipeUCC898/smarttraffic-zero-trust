package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ZoneTrafficSnapshotSerdeTest {
    private static final String TOPIC = "test-only-state";
    private final ZoneTrafficSnapshotSerde serde = new ZoneTrafficSnapshotSerde();

    @Test
    void roundTripsAnEmptyZoneWithoutInterpretingItAsATombstone() {
        var original = aggregate().snapshot();
        byte[] bytes = serde.serializer().serialize(TOPIC, original);
        assertThat(bytes).isNotNull();
        assertThat(serde.deserializer().deserialize(TOPIC, bytes)).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 20})
    void restoresEveryStateWithExactDataAndReplayProtection(int count) {
        var original = aggregate();
        var reading = observation(count);
        original.recordLatestObservation(reading);
        byte[] bytes = serde.serializer().serialize(TOPIC, original.snapshot());
        ZoneTrafficSnapshot decoded = serde.deserializer().deserialize(TOPIC, bytes);
        var restored = ZoneTrafficAggregate.restore(decoded,
                value -> BigDecimal.valueOf(value.vehicleCount()));
        assertThat(decoded).isEqualTo(original.snapshot());
        assertThat(restored.recordLatestObservation(reading)).isEmpty();
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("sensor-ñ", "\"formatVersion\":1")
                .doesNotContain("densityStrategy", "behavior", "@class");
    }

    @Test
    void preservesKafkaNullValuesWithoutAcceptingJsonNull() {
        assertThat(serde.serializer().serialize(TOPIC, null)).isNull();
        assertThat(serde.deserializer().deserialize(TOPIC, null)).isNull();
        assertThatThrownBy(() -> decode("null")).isInstanceOf(SerializationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rejectsCorruptIncompatibleOrCoercedState(String document) {
        assertThatThrownBy(() -> decode(document)).isInstanceOf(SerializationException.class);
    }

    @Test
    void rejectsMalformedUtf8RatherThanReplacingCharacters() {
        assertThatThrownBy(() -> serde.deserializer().deserialize(TOPIC, new byte[] {(byte) 0xc3, 0x28}))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void keepsCorruptStateAndTopicOutOfTheException() {
        assertThatThrownBy(() -> serde.deserializer().deserialize("private-topic", "private-state".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class)
                .hasMessage("Instantánea de zona inválida o formato no compatible")
                .hasNoCause();
    }

    @Test
    void supportsTheHeaderAwareKafkaInterfacesWithoutTypeHeaders() {
        var headers = new RecordHeaders();
        var snapshot = aggregate().snapshot();
        byte[] bytes = serde.serializer().serialize(TOPIC, headers, snapshot);
        assertThat(serde.deserializer().deserialize(TOPIC, headers, bytes)).isEqualTo(snapshot);
        assertThat(headers.toArray()).isEmpty();
    }

    @Test
    void requiresValueUsageAndCanCloseWithoutOpeningResources() {
        serde.configure(Map.of(), false);
        assertThatIllegalArgumentException().isThrownBy(() -> serde.configure(Map.of(), true));
        serde.close();
    }

    private ZoneTrafficSnapshot decode(String document) {
        return serde.deserializer().deserialize(TOPIC, document.getBytes(StandardCharsets.UTF_8));
    }

    private static Stream<String> invalidDocuments() {
        var aggregate = aggregate();
        aggregate.recordLatestObservation(observation(10));
        String valid = new String(new ZoneTrafficSnapshotSerde().serializer()
                .serialize(TOPIC, aggregate.snapshot()), StandardCharsets.UTF_8);
        return Stream.of("", "null", "[]", "{}", "{", valid + " {}",
                valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
                valid.replace("\"formatVersion\":1", "\"formatVersion\":1.0"),
                valid.replace("\"formatVersion\":1", "\"formatVersion\":\"1\""),
                valid.replace("\"formatVersion\":1,", ""),
                valid.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"),
                valid.replace("\"snapshot\":{", "\"snapshot\":{\"unknown\":1,"),
                valid.replace("\"zoneId\":\"north\"", "\"zoneId\":123"),
                valid.replaceFirst("\"zoneId\":\"north\"", "\"zoneId\":\"south\""),
                valid.replace("\"moderateFrom\":10.00", "\"moderateFrom\":\"10.00\""),
                valid.replace("\"moderateFrom\":10.00", "\"moderateFrom\":0"),
                valid.replace("\"vehicleCount\":10", "\"vehicleCount\":10.5"),
                valid.replace("\"vehicleCount\":10", "\"vehicleCount\":\"10\""),
                valid.replace("\"congestionState\":\"MODERADA\"", "\"congestionState\":1"),
                valid.replace("\"density\":10", "\"density\":-1"),
                valid.replace("\"density\":10", "\"density\":0"),
                valid.replace("\"measuredAt\":\"2026-01-01T00:00:00.123456789Z\"", "\"measuredAt\":123"),
                valid.replace("\"latestState\":", "\"missingState\":"),
                valid.replace("\"latestObservation\":", "\"missingObservation\":"),
                "{\"formatVersion\":1,\"snapshot\":null}");
    }

    private static ZoneTrafficAggregate aggregate() {
        return new ZoneTrafficAggregate("north", value -> BigDecimal.valueOf(value.vehicleCount()),
                new CongestionThresholds(new BigDecimal("10.00"), new BigDecimal("20.00")));
    }

    private static TrafficObservation observation(int count) {
        return new TrafficObservation(new UUID(0, 1), "sensor-ñ", "north",
                Instant.parse("2026-01-01T00:00:00.123456789Z"), count,
                new BigDecimal("35.500000000000000000001"), CongestionState.MODERADA);
    }
}
