package com.smarttraffic.processor.application.port.out;

import com.smarttraffic.processor.domain.model.ZoneTrafficState;

/** Puerto de publicación; el futuro adaptador RabbitMQ implementará este contrato. */
public interface TrafficStatePublisher {
    /**
     * Publica un estado no nulo. Un retorno normal expresa éxito según la garantía
     * declarada por el adaptador, que deberá verificarse antes de confirmar el estado.
     * No implica que dashboard o alertas ya hayan procesado el mensaje.
     * Los fallos deben propagarse; ante una respuesta incierta pueden existir duplicados.
     * Serialización, autenticación y confirmaciones pertenecen al adaptador futuro.
     *
     * @throws IllegalArgumentException si state es nulo
     */
    void publish(ZoneTrafficState state);
}
