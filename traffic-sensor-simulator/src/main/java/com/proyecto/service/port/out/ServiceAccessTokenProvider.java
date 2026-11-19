package com.proyecto.service.port.out;

import com.proyecto.dto.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
