package com.smarttraffic.processor.infrastructure.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic.auth")
public record AuthClientProperties(URI tokenEndpoint, String clientId, String clientSecret, long timeoutMs) {
    public AuthClientProperties {
        if (tokenEndpoint == null || !"https".equalsIgnoreCase(tokenEndpoint.getScheme())
                || tokenEndpoint.getHost() == null || tokenEndpoint.getUserInfo() != null
                || tokenEndpoint.getQuery() != null || tokenEndpoint.getFragment() != null) {
            throw new IllegalArgumentException("token-endpoint requiere HTTPS y no admite credenciales ni parámetros en la URL");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank() || timeoutMs < 1) {
            throw new IllegalArgumentException("Credenciales y timeout positivo son obligatorios");
        }
    }

    @Override
    public String toString() {
        return "AuthClientProperties[credentials=redacted]";
    }
}
