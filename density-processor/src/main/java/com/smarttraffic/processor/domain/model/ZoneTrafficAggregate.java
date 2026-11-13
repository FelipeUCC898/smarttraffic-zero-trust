package com.smarttraffic.processor.domain.model;

import com.smarttraffic.processor.domain.state.ClearTrafficState;
import com.smarttraffic.processor.domain.state.CongestionBehavior;
import com.smarttraffic.processor.domain.strategy.DensityCalculationStrategy;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Núcleo en memoria para una zona. No representa aún ventanas ni una KTable.
 * Cada instancia debe utilizarse desde un único hilo de procesamiento.
 */
public final class ZoneTrafficAggregate {
    private final String zoneId;
    private final DensityCalculationStrategy densityStrategy;
    private final CongestionThresholds thresholds;
    private CongestionBehavior behavior = new ClearTrafficState();
    private TrafficObservation latestObservation;
    private ZoneTrafficState latestState;

    public ZoneTrafficAggregate(String zoneId, DensityCalculationStrategy densityStrategy,
            CongestionThresholds thresholds) {
        DomainValidation.requireIdentifier(zoneId, "zoneId");
        DomainValidation.requirePresent(densityStrategy, "densityStrategy");
        DomainValidation.requirePresent(thresholds, "thresholds");
        this.zoneId = zoneId;
        this.densityStrategy = densityStrategy;
        this.thresholds = thresholds;
    }

    /**
     * Crea una instancia de trabajo con la misma identidad y estado confirmado.
     * Sus campos mutables pertenecen a la nueva instancia. Se comparten únicamente
     * valores inmutables, comportamientos State sin campos y la estrategia recibida
     * como dependencia de cálculo puro; no se clonan servicios externos.
     * Debe invocarse desde el hilo propietario, sin actualizaciones concurrentes.
     */
    public ZoneTrafficAggregate copyForProcessing() {
        ZoneTrafficAggregate copy = new ZoneTrafficAggregate(zoneId, densityStrategy, thresholds);
        copy.behavior = behavior;
        copy.latestObservation = latestObservation;
        copy.latestState = latestState;
        return copy;
    }

    /**
     * Modo explícito de última observación: no acumula conteos de sensores.
     * Devuelve vacío para la misma lectura o una fecha que no avance.
     */
    public Optional<ZoneTrafficState> recordLatestObservation(TrafficObservation observation) {
        DomainValidation.requirePresent(observation, "observation");
        if (!zoneId.equals(observation.zoneId())) {
            throw new IllegalArgumentException("La observación pertenece a otra zoneId");
        }
        if (latestObservation != null && latestObservation.eventId().equals(observation.eventId())) {
            if (!latestObservation.equals(observation)) {
                throw new IllegalArgumentException("eventId reutilizado con contenido diferente");
            }
            return Optional.empty();
        }
        if (latestState != null && !observation.measuredAt().isAfter(latestState.measuredAt())) {
            return Optional.empty();
        }

        BigDecimal density = densityStrategy.calculate(observation);
        DomainValidation.requireNonNegative(density, "density");
        CongestionBehavior nextBehavior = behavior.transition(density, thresholds);
        ZoneTrafficState nextState = new ZoneTrafficState(zoneId, observation.measuredAt(),
                density, nextBehavior.label(), null);

        // Confirmar el cambio solo después de que todo el cálculo y validación terminen.
        behavior = nextBehavior;
        latestObservation = observation;
        latestState = nextState;
        return Optional.of(nextState);
    }

    public Optional<ZoneTrafficState> currentState() {
        return Optional.ofNullable(latestState);
    }
}
