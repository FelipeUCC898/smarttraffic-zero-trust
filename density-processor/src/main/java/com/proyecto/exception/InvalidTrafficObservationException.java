package com.proyecto.exception;

/** Error de contrato de entrada; no representa un fallo del caso de uso o del broker. */
public final class InvalidTrafficObservationException extends IllegalArgumentException {
    public InvalidTrafficObservationException(String message) {
        super(message);
    }
}
