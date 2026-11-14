package com.smarttraffic.simulator.infrastructure.config;

import com.smarttraffic.simulator.domain.model.SensorScenario;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic.simulator")
public record SimulatorProperties(long intervalMs, List<SensorScenario> sensors) {
    public SimulatorProperties {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("traffic.simulator.interval-ms debe ser positivo");
        }
        if (sensors == null || sensors.isEmpty() || sensors.stream().anyMatch(sensor -> sensor == null)) {
            throw new IllegalArgumentException("traffic.simulator.sensors es obligatorio y no puede estar vacío");
        }
        sensors = List.copyOf(sensors);
    }
}
