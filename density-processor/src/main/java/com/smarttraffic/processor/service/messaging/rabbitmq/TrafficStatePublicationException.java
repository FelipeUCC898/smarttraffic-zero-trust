package com.smarttraffic.processor.service.messaging.rabbitmq;

/** Fallo de publicación; timeout/interrupción pueden dejar una entrega incierta. */
public final class TrafficStatePublicationException extends RuntimeException {
    public TrafficStatePublicationException(String message) {
        super(message);
    }
}
