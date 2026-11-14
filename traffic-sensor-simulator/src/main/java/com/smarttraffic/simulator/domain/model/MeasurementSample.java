package com.smarttraffic.simulator.domain.model;

import java.math.BigDecimal;

/** Muestra de un escenario explícito; no deriva congestión de una fórmula inventada. */
public record MeasurementSample(Integer vehicleCount, BigDecimal averageSpeedKmh, CongestionState congestionState) {
    public MeasurementSample {
        ReadingValidation.requireMeasurements(vehicleCount, averageSpeedKmh, congestionState);
    }
}
