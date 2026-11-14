package com.smarttraffic.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.domain.model.CongestionState;
import com.smarttraffic.simulator.domain.model.SensorReading;
import com.smarttraffic.simulator.infrastructure.output.LocalReadingLogger;
import com.smarttraffic.simulator.infrastructure.scheduling.SensorSimulationScheduler;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalSimulationTest {
    private static final SensorReading READING = new SensorReading(new UUID(0, 1), "sensor-ñ", "north",
            Instant.parse("2026-01-01T00:00:00Z"), 5, new BigDecimal("35.50"), CongestionState.MODERADA);

    @Test
    void serializesExactlyTheSevenContractFieldsAsJsonUtf8WithUtcDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        byte[] json = mapper.writeValueAsBytes(READING);
        var tree = mapper.readTree(json);

        assertThat(tree.size()).isEqualTo(7);
        assertThat(tree.get("eventId").asText()).isEqualTo(READING.eventId().toString());
        assertThat(tree.get("sensorId").asText()).isEqualTo("sensor-ñ");
        assertThat(tree.get("zoneId").asText()).isEqualTo("north");
        assertThat(tree.get("measuredAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(tree.get("vehicleCount").asInt()).isEqualTo(5);
        assertThat(tree.get("averageSpeedKmh").decimalValue()).isEqualByComparingTo("35.50");
        assertThat(tree.get("congestionState").asText()).isEqualTo("MODERADA");
        assertThat(new String(json, StandardCharsets.UTF_8)).contains("sensor-ñ");
        assertThat(mapper.readValue(json, SensorReading.class)).isEqualTo(READING);
    }

    @Test
    void schedulerGeneratesAndLogsTheBatchWithoutABroker() {
        SimulateTrafficService simulator = mock(SimulateTrafficService.class);
        LocalReadingLogger output = mock(LocalReadingLogger.class);
        when(simulator.generateBatch()).thenReturn(List.of(READING));

        new SensorSimulationScheduler(simulator, output).generate();

        verify(simulator).generateBatch();
        verify(output).log(READING);
    }

    @Test
    void skippedGenerationProducesNoOutputAndGenerationFailuresPropagate() {
        SimulateTrafficService simulator = mock(SimulateTrafficService.class);
        LocalReadingLogger output = mock(LocalReadingLogger.class);
        IllegalStateException failure = new IllegalStateException("Fallo de generación de prueba");
        when(simulator.generateBatch()).thenReturn(List.of()).thenThrow(failure);
        SensorSimulationScheduler scheduler = new SensorSimulationScheduler(simulator, output);

        scheduler.generate();
        assertThatThrownBy(scheduler::generate).isSameAs(failure);

        verifyNoInteractions(output);
    }

    @Test
    void localOutputDoesNotHideSerializationFailures() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        JsonProcessingException failure = new JsonProcessingException("Fallo JSON de prueba") { };
        when(mapper.writeValueAsString(READING)).thenThrow(failure);

        assertThatThrownBy(() -> new LocalReadingLogger(mapper).log(READING))
                .isInstanceOf(IllegalStateException.class).hasCause(failure);
    }
}
