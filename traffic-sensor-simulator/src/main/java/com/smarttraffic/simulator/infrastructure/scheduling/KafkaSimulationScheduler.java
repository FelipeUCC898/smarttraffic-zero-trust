package com.smarttraffic.simulator.infrastructure.scheduling;

import com.smarttraffic.simulator.application.port.out.TrafficObservationPublisher;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.domain.model.SensorReading;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pendientes en memoria, de un solo hilo; no sustituye un outbox durable. */
@Component
@Profile("kafka")
public class KafkaSimulationScheduler {
    private final SimulateTrafficService simulator;
    private final TrafficObservationPublisher publisher;
    private List<SensorReading> pending = List.of();
    private int next;

    public KafkaSimulationScheduler(SimulateTrafficService simulator, TrafficObservationPublisher publisher) {
        this.simulator = simulator;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${traffic.simulator.interval-ms}")
    public void publishNextBatch() {
        if (pending.isEmpty()) {
            pending = simulator.generateBatch();
            next = 0;
        }
        while (next < pending.size()) {
            publisher.publish(pending.get(next));
            next++;
        }
        pending = List.of();
        next = 0;
    }
}
