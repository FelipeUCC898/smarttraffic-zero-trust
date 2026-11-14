package com.smarttraffic.archiver.infrastructure.security;

import com.smarttraffic.archiver.infrastructure.security.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
