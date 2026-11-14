package com.smarttraffic.simulator.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Contrato JSON de observación compatible con los campos de raw_traffic_data. */
public record SensorReading(UUID eventId, String sensorId, String zoneId, Instant measuredAt,
        Integer vehicleCount, BigDecimal averageSpeedKmh, CongestionState congestionState) {
    public SensorReading {
        ReadingValidation.requirePresent(eventId, "eventId");
        ReadingValidation.requireIdentifier(sensorId, "sensorId");
        ReadingValidation.requireIdentifier(zoneId, "zoneId");
        ReadingValidation.requirePresent(measuredAt, "measuredAt");
        ReadingValidation.requireMeasurements(vehicleCount, averageSpeedKmh, congestionState);
    }
}
