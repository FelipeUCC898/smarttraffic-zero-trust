package com.smarttraffic.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic.auth")
public record AuthClientProperties(URI tokenEndpoint, String clientId, String clientSecret, long timeoutMs) {
    public AuthClientProperties {
        if (tokenEndpoint == null || clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || timeoutMs <= 0) throw new IllegalArgumentException("Configuración OAuth2 incompleta");
    }
}
