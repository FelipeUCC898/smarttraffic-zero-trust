package com.smarttraffic.processor.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Observación inmutable. No calcula densidad ni sustituye el DTO de transporte. */
public record TrafficObservation(
        UUID eventId,
        String sensorId,
        String zoneId,
        Instant measuredAt,
        Integer vehicleCount,
        BigDecimal averageSpeedKmh,
        CongestionState congestionState) {

    public TrafficObservation {
        DomainValidation.requirePresent(eventId, "eventId");
        DomainValidation.requireIdentifier(sensorId, "sensorId");
        DomainValidation.requireIdentifier(zoneId, "zoneId");
        DomainValidation.requirePresent(measuredAt, "measuredAt");
        DomainValidation.requirePresent(vehicleCount, "vehicleCount");
        if (vehicleCount < 0) {
            throw new IllegalArgumentException("vehicleCount no puede ser negativo");
        }
        DomainValidation.requireNonNegative(averageSpeedKmh, "averageSpeedKmh");
        DomainValidation.requirePresent(congestionState, "congestionState");
    }
}
