package com.smarttraffic.processor.infrastructure.security;

import java.time.Instant;

/** Credencial sensible; los metadatos no representan verificación criptográfica. */
public record ServiceAccessToken(String value, String principal, Instant issuedAt, Instant expiresAt) {
    public ServiceAccessToken {
        if (value == null || value.isBlank() || principal == null || principal.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("Token, principal y expiración son obligatorios");
        }
        if (issuedAt != null && !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("La expiración debe ser posterior a la emisión");
        }
    }

    @Override
    public String toString() {
        return "ServiceAccessToken[redacted]";
    }
}
