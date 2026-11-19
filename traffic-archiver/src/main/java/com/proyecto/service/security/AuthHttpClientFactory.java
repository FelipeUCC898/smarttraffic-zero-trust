package com.proyecto.service.security;

import com.proyecto.config.AuthClientProperties;
import com.proyecto.config.AuthTlsProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/** Construye clientes con confianza propia; no altera SSLContext global ni permite trust-all. */
public final class AuthHttpClientFactory {
    private AuthHttpClientFactory() { }

    public static HttpClient create(AuthClientProperties auth, AuthTlsProperties tls) {
        if (auth == null || tls == null) throw new IllegalArgumentException("Configuración HTTP/TLS obligatoria");
        var parameters = new SSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(auth.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).sslParameters(parameters);
        if (tls.caCertificate() == null && tls.trustStore() == null) return builder.build();
        try {
            KeyStore store;
            if (tls.caCertificate() != null) {
                store = KeyStore.getInstance("PKCS12"); store.load(null, null);
                try (var input = Files.newInputStream(tls.caCertificate())) {
                    var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
                    if (certificates.isEmpty()) throw new GeneralSecurityException("Certificados ausentes");
                    int index = 0;
                    for (var certificate : certificates) store.setCertificateEntry("auth-ca-" + index++, certificate);
                }
            } else {
                store = KeyStore.getInstance(tls.trustStoreType());
                char[] password = tls.trustStorePassword().toCharArray();
                try (var input = Files.newInputStream(tls.trustStore())) { store.load(input, password); }
                finally { Arrays.fill(password, '\0'); }
            }
            var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            var context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return builder.sslContext(context).build();
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cargar la confianza TLS del servicio de tokens");
        }
    }
}
