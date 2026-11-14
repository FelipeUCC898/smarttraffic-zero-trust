package com.smarttraffic.processor.infrastructure.security;

import com.smarttraffic.processor.infrastructure.security.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
