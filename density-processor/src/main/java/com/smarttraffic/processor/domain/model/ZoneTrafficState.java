package com.smarttraffic.processor.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Estado inmutable; la fórmula de densidad y la semántica de version están pendientes. */
public record ZoneTrafficState(
        String zoneId,
        Instant measuredAt,
        BigDecimal density,
        CongestionState congestionState,
        String version) {

    public ZoneTrafficState {
        DomainValidation.requireIdentifier(zoneId, "zoneId");
        DomainValidation.requirePresent(measuredAt, "measuredAt");
        DomainValidation.requireNonNegative(density, "density");
        DomainValidation.requirePresent(congestionState, "congestionState");
        // Se conserva version sin interpretarla ni exigir un formato no acordado.
    }
}
