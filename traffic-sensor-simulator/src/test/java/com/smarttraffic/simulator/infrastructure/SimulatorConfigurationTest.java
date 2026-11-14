package com.smarttraffic.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.infrastructure.config.LocalSimulatorConfiguration;
import com.smarttraffic.simulator.infrastructure.config.SimulatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SimulatorConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "traffic.simulator.interval-ms=1000",
                    "traffic.simulator.sensors[0].sensor-id=sensor-1",
                    "traffic.simulator.sensors[0].zone-id=north",
                    "traffic.simulator.sensors[0].samples[0].vehicle-count=5",
                    "traffic.simulator.sensors[0].samples[0].average-speed-kmh=35.5",
                    "traffic.simulator.sensors[0].samples[0].congestion-state=MODERADA");

    @Test
    void bindsExternalConfigurationToValidatedRecords() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            SimulatorProperties properties = context.getBean(SimulatorProperties.class);
            assertThat(properties.intervalMs()).isEqualTo(1000);
            assertThat(properties.sensors().get(0).sensorId()).isEqualTo("sensor-1");
            assertThat(properties.sensors().get(0).samples().get(0).vehicleCount()).isEqualTo(5);
        });
    }

    @Test
    void refusesInvalidFrequencyAndMeasurementsDuringBinding() {
        runner.withPropertyValues("traffic.simulator.interval-ms=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("traffic.simulator.sensors[0].samples[0].vehicle-count=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesMissingSensorConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("traffic.simulator.interval-ms=1000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void doesNotActivateLocalGenerationWithoutTheExplicitProfile() {
        new ApplicationContextRunner().withUserConfiguration(LocalSimulatorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SimulateTrafficService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SimulatorProperties.class)
    static class PropertiesConfiguration {
    }
}
