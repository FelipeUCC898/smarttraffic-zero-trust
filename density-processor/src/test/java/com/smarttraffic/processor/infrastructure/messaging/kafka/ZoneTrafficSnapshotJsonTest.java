package com.smarttraffic.processor.infrastructure.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import com.smarttraffic.processor.domain.model.ZoneTrafficAggregate;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Prueba de representación interna; no es un Serde ni un almacén Kafka. */
class ZoneTrafficSnapshotJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void reconstructsAnEmptyZoneAfterAJsonRoundTrip() throws Exception {
        var original = aggregate();
        byte[] bytes = mapper.writeValueAsBytes(original.snapshot());
        var restored = ZoneTrafficAggregate.restore(mapper.readValue(bytes, ZoneTrafficSnapshot.class),
                observation -> BigDecimal.valueOf(observation.vehicleCount()));
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 20})
    void preservesDataDecimalsAndReplayProtectionAcrossJson(int count) throws Exception {
        var original = aggregate();
        var observation = new TrafficObservation(new UUID(0, 1), "sensor-1", "north",
                Instant.parse("2026-01-01T00:00:00.123456789Z"), count,
                new BigDecimal("35.500000000000000000001"), CongestionState.MODERADA);
        original.recordLatestObservation(observation);

        byte[] bytes = mapper.writeValueAsBytes(original.snapshot());
        var document = mapper.readTree(bytes);
        assertThat(document.size()).isEqualTo(4);
        assertThat(document.has("densityStrategy")).isFalse();
        assertThat(document.has("behavior")).isFalse();
        var decoded = mapper.readValue(bytes, ZoneTrafficSnapshot.class);
        var restored = ZoneTrafficAggregate.restore(decoded,
                reading -> BigDecimal.valueOf(reading.vehicleCount()));

        assertThat(decoded).isEqualTo(original.snapshot());
        assertThat(restored.recordLatestObservation(observation)).isEmpty();
        assertThat(restored.snapshot()).isEqualTo(original.snapshot());
    }

    private static ZoneTrafficAggregate aggregate() {
        return new ZoneTrafficAggregate("north", observation -> BigDecimal.valueOf(observation.vehicleCount()),
                new CongestionThresholds(new BigDecimal("10.00"), new BigDecimal("20.00")));
    }
}
