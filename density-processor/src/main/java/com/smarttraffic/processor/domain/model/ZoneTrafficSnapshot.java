package com.smarttraffic.processor.domain.model;

/**
 * Datos inmutables para reconstruir el modo de última observación.
 * No contiene servicios, credenciales, offsets ni historial de eventos.
 * Ambos valores latest son null exclusivamente cuando la zona está vacía.
 */
public record ZoneTrafficSnapshot(
        String zoneId,
        CongestionThresholds thresholds,
        TrafficObservation latestObservation,
        ZoneTrafficState latestState) {

    public ZoneTrafficSnapshot {
        DomainValidation.requireIdentifier(zoneId, "zoneId");
        DomainValidation.requirePresent(thresholds, "thresholds");
        if ((latestObservation == null) != (latestState == null)) {
            throw new IllegalArgumentException("La observación y el estado deben estar presentes juntos");
        }
        if (latestObservation != null) {
            if (!zoneId.equals(latestObservation.zoneId()) || !zoneId.equals(latestState.zoneId())) {
                throw new IllegalArgumentException("La instantánea contiene otra zoneId");
            }
            if (!latestObservation.measuredAt().equals(latestState.measuredAt())) {
                throw new IllegalArgumentException("La observación y el estado deben tener la misma fecha");
            }
            CongestionState expected = thresholds.reachesCongested(latestState.density())
                    ? CongestionState.CONGESTIONADA
                    : thresholds.reachesModerate(latestState.density())
                            ? CongestionState.MODERADA : CongestionState.DESPEJADA;
            if (latestState.congestionState() != expected) {
                throw new IllegalArgumentException("El estado no corresponde a la densidad y los umbrales");
            }
        }
    }
}
