package com.smarttraffic.archiver;

import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/** Builds an HTTPS client that trusts only the configured Auth Server CA. */
final class AuthHttpClientFactory {
    private AuthHttpClientFactory() { }

    static java.net.http.HttpClient create(SecureKafkaProperties config) {
        var parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        var builder = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.timeoutMs()))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .sslParameters(parameters);
        if (config.caCertificate() == null && config.trustStore() == null) return builder.build();
        try {
            KeyStore store;
            if (config.caCertificate() != null) {
                store = KeyStore.getInstance("PKCS12");
                store.load(null, null);
                try (var input = Files.newInputStream(config.caCertificate())) {
                    var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
                    if (certificates.isEmpty()) throw new GeneralSecurityException("CA vacía");
                    int index = 0;
                    for (var certificate : certificates) {
                        store.setCertificateEntry("auth-ca-" + index++, certificate);
                    }
                }
            } else {
                store = KeyStore.getInstance(config.trustStoreType());
                char[] password = config.trustStorePassword().toCharArray();
                try (var input = Files.newInputStream(config.trustStore())) {
                    store.load(input, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
            var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            var context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return builder.sslContext(context).build();
        } catch (IOException | GeneralSecurityException | NullPointerException exception) {
            throw new IllegalStateException("No se pudo cargar la confianza TLS del Auth Server");
        }
    }
}
