package com.smarttraffic.archiver;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("traffic.archiver.auth")
record SecureKafkaProperties(URI tokenEndpoint, String clientId, String clientSecret,
        Path caCertificate, Path trustStore, String trustStoreType, String trustStorePassword,
        long timeoutMs) {
    SecureKafkaProperties {
        if (tokenEndpoint == null || !"https".equalsIgnoreCase(tokenEndpoint.getScheme())
                || clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new IllegalArgumentException("El archivador seguro requiere endpoint HTTPS y credenciales externas");
        if (timeoutMs <= 0) throw new IllegalArgumentException("El timeout TLS debe ser positivo");
    }
}
