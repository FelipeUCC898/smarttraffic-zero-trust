package com.smarttraffic.processor.domain.model;

import java.math.BigDecimal;

/** Validaciones compartidas internas; no modifica los valores recibidos. */
final class DomainValidation {
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private DomainValidation() {
    }

    static void requirePresent(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
    }

    static void requireIdentifier(String value, String field) {
        requirePresent(value, field);
        if (value.isBlank() || value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(field + " debe tener entre 1 y 128 caracteres y no estar vacío");
        }
    }

    static void requireNonNegative(BigDecimal value, String field) {
        requirePresent(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " no puede ser negativo");
        }
    }
}
