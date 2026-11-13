package com.smarttraffic.processor.application.model;

import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import java.util.Optional;
import java.util.UUID;

/** Resultado interno del caso de uso; no es un contrato de mensajería. */
public record ProcessingResult(UUID eventId, Outcome outcome, Optional<ZoneTrafficState> state) {
    public ProcessingResult {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId es obligatorio");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome es obligatorio");
        }
        if (state == null) {
            throw new IllegalArgumentException("state debe ser un Optional no nulo");
        }
        if ((outcome == Outcome.PROCESSED) != state.isPresent()) {
            throw new IllegalArgumentException("PROCESSED requiere estado e IGNORED requiere estado vacío");
        }
    }

    public static ProcessingResult processed(UUID eventId, ZoneTrafficState state) {
        if (state == null) {
            throw new IllegalArgumentException("state es obligatorio para PROCESSED");
        }
        return new ProcessingResult(eventId, Outcome.PROCESSED, Optional.of(state));
    }

    public static ProcessingResult ignored(UUID eventId) {
        return new ProcessingResult(eventId, Outcome.IGNORED, Optional.empty());
    }

    public enum Outcome {
        PROCESSED,
        IGNORED
    }
}
