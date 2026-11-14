package com.smarttraffic.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.smarttraffic.simulator.SimulatorApplication;
import com.smarttraffic.simulator.infrastructure.scheduling.SensorSimulationScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.kafka.core.KafkaTemplate;

class LocalProfileStartupTest {
    @Test
    void startsAndClosesTheRealLocalApplicationWithoutKafkaClients() {
        try (var context = new SpringApplicationBuilder(SimulatorApplication.class)
                .web(WebApplicationType.NONE).profiles("local")
                .run("--spring.main.banner-mode=off", "--logging.level.root=WARN",
                        "--logging.level.org.springframework.boot=WARN",
                        "--traffic.simulator.interval-ms=60000")) {
            assertThat(context.getBeansOfType(SensorSimulationScheduler.class)).hasSize(1);
            assertThat(context.getBeansOfType(KafkaTemplate.class)).isEmpty();
        }
    }
}
