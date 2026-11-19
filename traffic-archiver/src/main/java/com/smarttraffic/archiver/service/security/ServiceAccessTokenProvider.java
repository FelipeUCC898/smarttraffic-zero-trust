package com.smarttraffic.archiver.service.security;

import com.smarttraffic.archiver.service.security.ServiceAccessToken;

/** Solicita una credencial nueva; Kafka controla cuándo requiere renovación. */
public interface ServiceAccessTokenProvider {
    ServiceAccessToken obtainToken();
}
