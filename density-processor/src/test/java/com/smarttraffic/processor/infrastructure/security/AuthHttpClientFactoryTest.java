package com.smarttraffic.processor.service.security;

import static org.assertj.core.api.Assertions.*;
import com.smarttraffic.processor.config.AuthClientProperties;
import com.smarttraffic.processor.config.AuthTlsProperties;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** HTTPS real contra servidor efímero local; certificados generados solo para este test. */
class AuthHttpClientFactoryTest {
    private static final String PASSWORD = "test-only-password";
    @TempDir static Path directory;
    private static Path store, certificate;
    private static HttpsServer server;
    private static URI endpoint;

    @BeforeAll static void startFixture() throws Exception {
        store = directory.resolve("test-auth.p12");
        certificate = directory.resolve("test-auth.pem");
        keytool("-genkeypair", "-alias", "test-auth", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", PASSWORD, "-noprompt");
        keytool("-exportcert", "-rfc", "-alias", "test-auth", "-keystore", store.toString(),
                "-storepass", PASSWORD, "-file", certificate.toString());
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(store)) { keys.load(input, PASSWORD.toCharArray()); }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, PASSWORD.toCharArray());
        var context = SSLContext.getInstance("TLS"); context.init(managers.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        server.createContext("/token", exchange -> {
            byte[] body = "test-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start(); endpoint = URI.create("https://127.0.0.1:" + server.getAddress().getPort() + "/token");
    }
    @AfterAll static void stopFixture() { if (server != null) server.stop(0); }

    @Test void acceptsTheExplicitPemCertificateAndPreservesHostnameVerification() throws Exception {
        var defaultContext = SSLContext.getDefault();
        var client = AuthHttpClientFactory.create(auth(), new AuthTlsProperties(certificate, null, null, null));
        assertThat(response(client)).isEqualTo("test-ok");
        assertThat(client.sslParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(SSLContext.getDefault()).isSameAs(defaultContext);
    }
    @Test void acceptsAnExplicitPkcs12Truststore() throws Exception {
        var client = AuthHttpClientFactory.create(auth(), new AuthTlsProperties(null, store, "PKCS12", PASSWORD));
        assertThat(response(client)).isEqualTo("test-ok");
    }
    @Test void rejectsTheUntrustedSelfSignedFixtureWithJvmDefaultTrust() {
        var client = AuthHttpClientFactory.create(auth(), new AuthTlsProperties(null, null, null, null));
        assertThatThrownBy(() -> response(client)).isInstanceOf(SSLHandshakeException.class);
    }
    @Test void missingCertificateFailsWithoutFallingBack() {
        assertThatThrownBy(() -> AuthHttpClientFactory.create(auth(), new AuthTlsProperties(directory.resolve("absent.pem"), null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }
    @Test void invalidPemFailsWithoutFallingBack() throws Exception {
        Path invalid = directory.resolve("invalid.pem"); Files.writeString(invalid, "not-a-certificate");
        assertThatThrownBy(() -> AuthHttpClientFactory.create(auth(), new AuthTlsProperties(invalid, null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }
    @Test void wrongTruststorePasswordDoesNotAppearInDiagnostics() {
        assertThatThrownBy(() -> AuthHttpClientFactory.create(auth(), new AuthTlsProperties(null, store, "PKCS12", "wrong-secret")))
                .isInstanceOf(IllegalStateException.class).hasMessage("No se pudo cargar la confianza TLS del servicio de tokens").hasNoCause();
        assertThat(new AuthTlsProperties(null, store, "PKCS12", PASSWORD).toString()).doesNotContain(PASSWORD, store.toString());
    }
    @Test void rejectsAmbiguousOrIncompleteTlsSettings() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthTlsProperties(certificate, store, null, PASSWORD));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthTlsProperties(null, null, null, PASSWORD));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthTlsProperties(null, store, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthTlsProperties(null, store, "unsupported", PASSWORD));
    }
    @Test void requiresExplicitConfigurationObjects() {
        assertThatIllegalArgumentException().isThrownBy(() -> AuthHttpClientFactory.create(null, new AuthTlsProperties(null, null, null, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> AuthHttpClientFactory.create(auth(), null));
    }
    private static AuthClientProperties auth() { return new AuthClientProperties(endpoint, "test-client", "test-only-secret", 5000); }
    private static String response(HttpClient client) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }
    private static void keytool(String... arguments) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "keytool.exe" : "keytool";
        List<String> command = new ArrayList<>(); command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.addAll(Arrays.asList(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve("keytool-fixture.log").toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("keytool de la fixture terminó").isTrue();
        assertThat(process.exitValue()).as("keytool de la fixture generó el certificado local").isZero();
    }
}
