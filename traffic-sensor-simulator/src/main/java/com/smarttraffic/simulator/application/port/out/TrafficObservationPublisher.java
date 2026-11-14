package com.smarttraffic.simulator.application.port.out;

import com.smarttraffic.simulator.domain.model.SensorReading;

/** Un retorno normal indica confirmación del adaptador, no procesamiento del consumidor. */
public interface TrafficObservationPublisher {
    void publish(SensorReading reading);
}
