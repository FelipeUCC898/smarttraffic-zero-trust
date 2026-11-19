package com.proyecto.service;

import com.proyecto.dto.ProcessingResult;
import com.proyecto.service.port.in.ProcessTrafficObservationUseCase;
import com.proyecto.service.port.out.TrafficStatePublisher;
import com.proyecto.repository.ZoneTrafficStateStore;
import com.proyecto.model.TrafficObservation;
import com.proyecto.model.ZoneTrafficAggregate;
import com.proyecto.model.ZoneTrafficState;
import java.util.Optional;

/**
 * Coordina el dominio mediante puertos, sin dependencias de infraestructura.
 * El llamador debe serializar el procesamiento por zona; no hay bloqueo ni
 * transacción distribuida entre publicación y guardado.
 */
public final class ProcessTrafficObservationService implements ProcessTrafficObservationUseCase {
    private final ZoneTrafficStateStore stateStore;
    private final TrafficStatePublisher publisher;

    public ProcessTrafficObservationService(ZoneTrafficStateStore stateStore, TrafficStatePublisher publisher) {
        if (stateStore == null) {
            throw new IllegalArgumentException("stateStore es obligatorio");
        }
        if (publisher == null) {
            throw new IllegalArgumentException("publisher es obligatorio");
        }
        this.stateStore = stateStore;
        this.publisher = publisher;
    }

    @Override
    public ProcessingResult process(TrafficObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation es obligatorio");
        }

        ZoneTrafficAggregate confirmed = stateStore.getOrCreate(observation.zoneId());
        if (confirmed == null) {
            throw new IllegalStateException("stateStore devolvió un agregado nulo");
        }
        ZoneTrafficAggregate workingCopy = confirmed.copyForProcessing();
        Optional<ZoneTrafficState> update = workingCopy.recordLatestObservation(observation);
        if (update.isEmpty()) {
            return ProcessingResult.ignored(observation.eventId());
        }

        ZoneTrafficState state = update.orElseThrow();
        ProcessingResult result = ProcessingResult.processed(observation.eventId(), state);
        publisher.publish(state);
        stateStore.save(observation.zoneId(), workingCopy);
        return result;
    }
}
