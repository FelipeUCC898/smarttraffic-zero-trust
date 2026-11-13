package com.smarttraffic.processor.domain.model;

import java.math.BigDecimal;

/** Límites inclusivos de entrada a MODERADA y CONGESTIONADA, sin valores por defecto. */
public record CongestionThresholds(BigDecimal moderateFrom, BigDecimal congestedFrom) {
    public CongestionThresholds {
        DomainValidation.requirePresent(moderateFrom, "moderateFrom");
        DomainValidation.requirePresent(congestedFrom, "congestedFrom");
        if (moderateFrom.signum() <= 0) {
            throw new IllegalArgumentException("moderateFrom debe ser positivo");
        }
        if (congestedFrom.compareTo(moderateFrom) <= 0) {
            throw new IllegalArgumentException("congestedFrom debe ser mayor que moderateFrom");
        }
    }

    public boolean reachesModerate(BigDecimal density) {
        DomainValidation.requireNonNegative(density, "density");
        return density.compareTo(moderateFrom) >= 0;
    }

    public boolean reachesCongested(BigDecimal density) {
        DomainValidation.requireNonNegative(density, "density");
        return density.compareTo(congestedFrom) >= 0;
    }
}
