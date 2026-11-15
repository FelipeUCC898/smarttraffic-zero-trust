package com.smarttraffic.archiver;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("traffic.archiver.auth")
record SecureKafkaProperties(URI tokenEndpoint, String clientId, String clientSecret) {
    SecureKafkaProperties {
        if (tokenEndpoint == null || !"https".equalsIgnoreCase(tokenEndpoint.getScheme())
                || clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new IllegalArgumentException("El archivador seguro requiere endpoint HTTPS y credenciales externas");
    }
}
