package com.proyecto.service.port.out;

import com.proyecto.model.SensorReading;

/** Un retorno normal indica confirmación del adaptador, no procesamiento del consumidor. */
public interface TrafficObservationPublisher {
    void publish(SensorReading reading);
}
