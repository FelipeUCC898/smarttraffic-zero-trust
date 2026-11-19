package com.smarttraffic.simulator.repository;

import com.smarttraffic.simulator.model.SensorReading;

/** Un retorno normal indica confirmación del adaptador, no procesamiento del consumidor. */
public interface TrafficObservationPublisher {
    void publish(SensorReading reading);
}
