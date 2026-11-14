package com.smarttraffic.simulator.application.service;

import com.smarttraffic.simulator.domain.model.MeasurementSample;
import com.smarttraffic.simulator.domain.model.SensorReading;
import com.smarttraffic.simulator.domain.model.SensorScenario;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Generación de un solo hilo, con reloj e identidad inyectados para pruebas. */
public final class SimulateTrafficService {
    private final List<SensorScenario> sensors;
    private final Clock clock;
    private final Supplier<UUID> eventIds;
    private final int[] nextSample;
    private Instant lastMeasurement;

    public SimulateTrafficService(List<SensorScenario> sensors, Clock clock, Supplier<UUID> eventIds) {
        if (sensors == null || sensors.isEmpty() || clock == null || eventIds == null) {
            throw new IllegalArgumentException("sensors, clock y eventIds son obligatorios");
        }
        Set<String> sensorIds = new HashSet<>();
        for (SensorScenario sensor : sensors) {
            if (sensor == null || !sensorIds.add(sensor.sensorId())) {
                throw new IllegalArgumentException("Cada sensor debe ser no nulo y tener sensorId único");
            }
        }
        this.sensors = List.copyOf(sensors);
        this.clock = clock;
        this.eventIds = eventIds;
        this.nextSample = new int[sensors.size()];
    }

    public List<SensorReading> generateBatch() {
        Instant measuredAt = clock.instant();
        if (lastMeasurement != null && !measuredAt.isAfter(lastMeasurement)) {
            return List.of();
        }
        List<SensorReading> readings = new ArrayList<>();
        Set<UUID> batchIds = new HashSet<>();
        for (int index = 0; index < sensors.size(); index++) {
            SensorScenario sensor = sensors.get(index);
            MeasurementSample sample = sensor.samples().get(nextSample[index]);
            SensorReading reading = new SensorReading(eventIds.get(), sensor.sensorId(), sensor.zoneId(),
                    measuredAt, sample.vehicleCount(), sample.averageSpeedKmh(), sample.congestionState());
            if (!batchIds.add(reading.eventId())) {
                throw new IllegalArgumentException("eventIds devolvió un UUID duplicado dentro del lote");
            }
            readings.add(reading);
        }
        for (int index = 0; index < sensors.size(); index++) {
            nextSample[index] = (nextSample[index] + 1) % sensors.get(index).samples().size();
        }
        lastMeasurement = measuredAt;
        return List.copyOf(readings);
    }
}
