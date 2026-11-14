package com.smarttraffic.simulator.infrastructure.scheduling;

import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.infrastructure.output.LocalReadingLogger;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class SensorSimulationScheduler {
    private final SimulateTrafficService simulator;
    private final LocalReadingLogger output;

    public SensorSimulationScheduler(SimulateTrafficService simulator, LocalReadingLogger output) {
        this.simulator = simulator;
        this.output = output;
    }

    @Scheduled(fixedDelayString = "${traffic.simulator.interval-ms}")
    public void generate() {
        simulator.generateBatch().forEach(output::log);
    }
}
