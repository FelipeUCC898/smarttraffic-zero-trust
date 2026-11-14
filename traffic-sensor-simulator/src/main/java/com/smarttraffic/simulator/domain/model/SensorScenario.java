package com.smarttraffic.simulator.domain.model;

import java.util.List;

/** Secuencia cíclica de muestras configuradas para un sensor. */
public record SensorScenario(String sensorId, String zoneId, List<MeasurementSample> samples) {
    public SensorScenario {
        ReadingValidation.requireIdentifier(sensorId, "sensorId");
        ReadingValidation.requireIdentifier(zoneId, "zoneId");
        if (samples == null || samples.isEmpty() || samples.stream().anyMatch(sample -> sample == null)) {
            throw new IllegalArgumentException("samples debe contener al menos una muestra no nula");
        }
        samples = List.copyOf(samples);
    }
}
