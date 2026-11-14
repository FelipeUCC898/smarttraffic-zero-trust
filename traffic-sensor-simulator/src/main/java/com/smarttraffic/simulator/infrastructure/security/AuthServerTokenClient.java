package com.smarttraffic.simulator.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.application.model.ServiceAccessToken;
import com.smarttraffic.simulator.application.port.out.ServiceAccessTokenProvider;
import com.smarttraffic.simulator.infrastructure.config.AuthClientProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/** Cliente del contrato actual de A: JSON clientId/clientSecret y respuesta accessToken. */
public final class AuthServerTokenClient implements ServiceAccessTokenProvider {
    private static final int MAX_RESPONSE_BYTES = 65536;
    private final AuthClientProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuthServerTokenClient(AuthClientProperties properties, HttpClient http, ObjectMapper mapper, Clock clock) {
        if (properties == null || http == null || mapper == null || clock == null) {
            throw new IllegalArgumentException("Configuración, cliente HTTP, mapper y reloj son obligatorios");
        }
        this.properties = properties;
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ServiceAccessToken obtainToken() {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "clientId", properties.clientId(), "clientSecret", properties.clientSecret()));
            HttpRequest request = HttpRequest.newBuilder(properties.tokenEndpoint())
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var pending = http.sendAsync(request, boundedBody());
            HttpResponse<byte[]> response;
            try {
                response = pending.get(properties.timeoutMs(), TimeUnit.MILLISECONDS);
            } finally {
                if (!pending.isDone()) {
                    pending.cancel(true);
                }
            }
            if (response.statusCode() != 200 || response.body() == null || response.body().length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Respuesta de autenticación no aceptable");
            }
            JsonNode token = mapper.readTree(response.body()).get("accessToken");
            if (token == null || !token.isTextual()) {
                throw new IllegalStateException("Respuesta de autenticación sin accessToken");
            }
            return readMetadata(token.textValue());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Solicitud de token interrumpida");
        } catch (Exception exception) {
            // No adjuntar respuesta, credenciales, JWT ni errores de parsing que puedan contenerlos.
            throw new IllegalStateException("No se pudo obtener un JWT utilizable para el cliente");
        }
    }

    static HttpResponse.BodyHandler<byte[]> boundedBody() {
        return info -> new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            private Flow.Subscription subscription;
            private long bytes;

            @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            @Override public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                delegate.onSubscribe(value);
            }
            @Override public void onNext(List<ByteBuffer> chunks) {
                for (ByteBuffer chunk : chunks) {
                    bytes += chunk.remaining();
                }
                if (bytes > MAX_RESPONSE_BYTES) {
                    subscription.cancel();
                    delegate.onError(new IllegalStateException("Respuesta de autenticación demasiado grande"));
                } else {
                    delegate.onNext(chunks);
                }
            }
            @Override public void onError(Throwable failure) { delegate.onError(failure); }
            @Override public void onComplete() { delegate.onComplete(); }
        };
    }

    private ServiceAccessToken readMetadata(String value) throws Exception {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || parts[2].isBlank()) {
            throw new IllegalArgumentException("Formato JWT no aceptable");
        }
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        if (!header.path("alg").isTextual() || header.path("alg").asText().isBlank()
                || "none".equalsIgnoreCase(header.path("alg").asText())) {
            throw new IllegalArgumentException("Se requiere JWT firmado");
        }
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode exp = claims.get("exp");
        if (exp == null || !exp.isIntegralNumber() || !exp.canConvertToLong()
                || !claims.path("sub").isTextual() || !properties.clientId().equals(claims.path("sub").asText())) {
            throw new IllegalArgumentException("Metadatos de token no aceptables");
        }
        Instant expires = Instant.ofEpochSecond(exp.longValue());
        Instant issued = null;
        if (claims.has("iat")) {
            JsonNode iat = claims.get("iat");
            if (!iat.isIntegralNumber() || !iat.canConvertToLong()) {
                throw new IllegalArgumentException("iat no aceptable");
            }
            issued = Instant.ofEpochSecond(iat.longValue());
        }
        Instant now = clock.instant();
        if (!expires.isAfter(now) || issued != null && issued.isAfter(now)) {
            throw new IllegalArgumentException("Token expirado o emitido en el futuro");
        }
        // Solo metadatos para el cliente: el broker debe verificar firma, permisos y claims.
        return new ServiceAccessToken(value, claims.path("sub").asText(), issued, expires);
    }
}
