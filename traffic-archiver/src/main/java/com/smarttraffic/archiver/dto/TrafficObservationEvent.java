package com.smarttraffic.archiver.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TrafficObservationEvent(UUID eventId, String sensorId, String zoneId, Instant measuredAt,
                               Integer vehicleCount, BigDecimal averageSpeedKmh,
                               CongestionState congestionState) {
    public enum CongestionState {
        DESPEJADA, MODERADA, CONGESTIONADA
    }

    public void validate(String key) {
        if (eventId == null || measuredAt == null || vehicleCount == null || vehicleCount < 0
                || averageSpeedKmh == null || averageSpeedKmh.signum() < 0 || congestionState == null
                || sensorId == null || sensorId.isBlank() || sensorId.length() > 128
                || zoneId == null || zoneId.isBlank() || zoneId.length() > 128
                || !sensorId.equals(key)) {
            throw new IllegalArgumentException("Invalid traffic observation or Kafka key");
        }
    }
}
