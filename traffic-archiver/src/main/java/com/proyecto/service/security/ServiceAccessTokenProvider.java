package com.proyecto.service.security;

import com.proyecto.service.security.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
