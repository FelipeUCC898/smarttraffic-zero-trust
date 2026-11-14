package com.smarttraffic.simulator.application.port.out;

import com.smarttraffic.simulator.application.model.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
