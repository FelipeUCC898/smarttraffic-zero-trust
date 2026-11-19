package com.smarttraffic.simulator.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Confianza del cliente HTTPS de tokens; no contiene claves de firma ni de cliente. */
@ConfigurationProperties("traffic.auth.tls")
public record AuthTlsProperties(Path caCertificate, Path trustStore, String trustStoreType, String trustStorePassword) {
    public AuthTlsProperties {
        if (caCertificate != null && trustStore != null)
            throw new IllegalArgumentException("Configurar CA PEM o truststore HTTPS, no ambos");
        if (trustStore == null && trustStorePassword != null)
            throw new IllegalArgumentException("La contraseña de truststore requiere un truststore");
        if (trustStore != null && (trustStorePassword == null || trustStorePassword.isBlank()))
            throw new IllegalArgumentException("El truststore HTTPS requiere contraseña de integridad");
        trustStoreType = trustStoreType == null ? "PKCS12" : trustStoreType;
        if (!"PKCS12".equalsIgnoreCase(trustStoreType) && !"JKS".equalsIgnoreCase(trustStoreType))
            throw new IllegalArgumentException("El truststore HTTPS debe ser PKCS12 o JKS");
    }
    @Override public String toString() { return "AuthTlsProperties[configuration=redacted]"; }
}
