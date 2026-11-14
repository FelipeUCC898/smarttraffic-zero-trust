package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RawTrafficObservationDecoderTest {
    static final String JSON = """
            {"eventId":"ca620302-913d-49de-af36-bb1173016f9c","sensorId":"sensor-1","zoneId":"north",
             "measuredAt":"2026-01-01T00:00:00Z","vehicleCount":42,"averageSpeedKmh":35.50,
             "congestionState":"MODERADA"}
            """;
    private final RawTrafficObservationDecoder decoder = new RawTrafficObservationDecoder(mapper());

    @Test
    void decodesTheExistingContractWithoutLosingDecimalScaleOrIdentity() {
        TrafficObservation observation = decoder.decode("sensor-1", bytes(JSON));

        assertThat(observation.eventId()).isEqualTo(UUID.fromString("ca620302-913d-49de-af36-bb1173016f9c"));
        assertThat(observation.sensorId()).isEqualTo("sensor-1");
        assertThat(observation.zoneId()).isEqualTo("north");
        assertThat(observation.measuredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(observation.vehicleCount()).isEqualTo(42);
        assertThat(observation.averageSpeedKmh()).isEqualTo(new BigDecimal("35.50"));
        assertThat(observation.congestionState()).isEqualTo(CongestionState.MODERADA);
    }

    @Test
    void preservesUtf8IdentifiersAndHighPrecisionDecimals() {
        String json = JSON.replace("sensor-1", "sensor-ñ").replace("35.50", "35.123456789012345678901234567890");

        TrafficObservation observation = decoder.decode("sensor-ñ", bytes(json));

        assertThat(observation.sensorId()).isEqualTo("sensor-ñ");
        assertThat(observation.averageSpeedKmh()).isEqualTo(new BigDecimal("35.123456789012345678901234567890"));
    }

    @Test
    void toleratesAdditionalFieldsWithoutChangingTheRequiredContract() {
        TrafficObservation observation = decoder.decode("sensor-1", bytes(JSON.replace("\"vehicleCount\":42",
                "\"extra\":{\"description\":\"extension\"},\"vehicleCount\":42")));

        assertThat(observation.vehicleCount()).isEqualTo(42);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "other-sensor"})
    void rejectsMissingOrMismatchedKey(String key) {
        assertInvalid(key, bytes(JSON));
    }

    @Test
    void rejectsTombstonesEmptyValuesAndMalformedUtf8() {
        assertInvalid("sensor-1", null);
        assertInvalid("sensor-1", new byte[0]);
        assertInvalid("sensor-1", new byte[]{(byte) 0xc3, (byte) 0x28});
    }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rejectsMalformedDocumentsMissingFieldsAndWrongTypes(String json) {
        assertInvalid("sensor-1", bytes(json));
    }

    @Test
    void preservesTheInjectedMapperConfigurationAndNeverLeaksAnInvalidPayload() {
        ObjectMapper original = mapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        RawTrafficObservationDecoder isolated = new RawTrafficObservationDecoder(original);

        isolated.decode("sensor-1", bytes(JSON.replace("\"vehicleCount\":42", "\"extra\":true,\"vehicleCount\":42")));

        assertThat(original.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
        assertThatThrownBy(() -> isolated.decode("sensor-1", bytes("{ untrusted-sensitive-test-value")))
                .isInstanceOf(InvalidTrafficObservationException.class).hasNoCause()
                .hasMessage("El mensaje no cumple el contrato de observación UTF-8 JSON");
        assertThatIllegalArgumentException().isThrownBy(() -> new RawTrafficObservationDecoder(null));
    }

    private void assertInvalid(String key, byte[] value) {
        assertThatThrownBy(() -> decoder.decode(key, value)).isInstanceOf(InvalidTrafficObservationException.class);
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static Stream<String> invalidDocuments() {
        Stream<String> missing = Stream.of("eventId", "sensorId", "zoneId", "measuredAt", "vehicleCount",
                "averageSpeedKmh", "congestionState").map(field -> {
                    try {
                        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper().readTree(JSON);
                        tree.remove(field);
                        return tree.toString();
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
        Stream<String> invalid = Stream.of("null", "[]", "{}", "{", "\"text\"", JSON + "{}",
                JSON.replace("\"vehicleCount\":42", "\"vehicleCount\":1,\"vehicleCount\":42"),
                JSON.replace("\"vehicleCount\":42", "\"vehicleCount\":42.5"),
                JSON.replace("\"vehicleCount\":42", "\"vehicleCount\":\"42\""),
                JSON.replace("\"vehicleCount\":42", "\"vehicleCount\":2147483648"),
                JSON.replace("\"vehicleCount\":42", "\"vehicleCount\":-1"),
                JSON.replace("\"averageSpeedKmh\":35.50", "\"averageSpeedKmh\":-1"),
                JSON.replace("\"averageSpeedKmh\":35.50", "\"averageSpeedKmh\":\"35.50\""),
                JSON.replace("\"congestionState\":\"MODERADA\"", "\"congestionState\":1"),
                JSON.replace("MODERADA", "UNKNOWN"),
                JSON.replace("\"sensorId\":\"sensor-1\"", "\"sensorId\":1"),
                JSON.replace("\"zoneId\":\"north\"", "\"zoneId\":\" \""),
                JSON.replace("\"zoneId\":\"north\"", "\"zoneId\":\"" + "z".repeat(129) + "\""),
                JSON.replace("\"measuredAt\":\"2026-01-01T00:00:00Z\"", "\"measuredAt\":1767225600"),
                JSON.replace("2026-01-01T00:00:00Z", "not-a-date"),
                JSON.replace("ca620302-913d-49de-af36-bb1173016f9c", "1-1-1-1-1"));
        return Stream.concat(missing, invalid);
    }
}
