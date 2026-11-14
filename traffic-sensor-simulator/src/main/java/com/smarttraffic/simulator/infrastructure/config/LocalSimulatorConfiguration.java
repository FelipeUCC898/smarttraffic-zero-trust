package com.smarttraffic.simulator.infrastructure.config;

import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("local")
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class LocalSimulatorConfiguration {
    @Bean
    SimulateTrafficService simulateTrafficService(SimulatorProperties properties) {
        return new SimulateTrafficService(properties.sensors(), Clock.systemUTC(), UUID::randomUUID);
    }
}
