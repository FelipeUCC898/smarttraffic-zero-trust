package com.smarttraffic.simulator.domain.model;

import java.math.BigDecimal;

final class ReadingValidation {
    private ReadingValidation() {
    }

    static void requirePresent(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }

    static void requireIdentifier(String value, String field) {
        requirePresent(value, field);
        if (value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " debe ser no vacío y tener hasta 128 caracteres");
        }
    }

    static void requireMeasurements(Integer count, BigDecimal speed, CongestionState state) {
        requirePresent(count, "vehicleCount");
        requirePresent(speed, "averageSpeedKmh");
        requirePresent(state, "congestionState");
        if (count < 0 || speed.signum() < 0) {
            throw new IllegalArgumentException("Las mediciones no pueden ser negativas");
        }
    }
}
