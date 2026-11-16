package com.smarttraffic.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public final class AuthTokenClient implements ServiceAccessTokenProvider {
    private final AuthClientProperties properties; private final ObjectMapper mapper; private final HttpClient http; private final Clock clock;
    public AuthTokenClient(AuthClientProperties properties, ObjectMapper mapper, Clock clock) {
        this.properties = properties; this.mapper = mapper; this.clock = clock; this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs())).build();
    }
    @Override public ServiceAccessToken obtainToken() {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of("clientId", properties.clientId(), "clientSecret", properties.clientSecret()));
            var request = HttpRequest.newBuilder(properties.tokenEndpoint()).timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 65536) throw new IllegalStateException("Respuesta OAuth2 no aceptable");
            JsonNode node = mapper.readTree(response.body()).get("accessToken");
            if (node == null || !node.isTextual()) throw new IllegalStateException("Respuesta OAuth2 sin accessToken");
            return metadata(node.asText());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Solicitud OAuth2 interrumpida"); }
        catch (Exception e) { throw new IllegalStateException("No se pudo obtener un JWT utilizable"); }
    }
    private ServiceAccessToken metadata(String value) throws Exception {
        String[] parts = value.split("\\.", -1); if (parts.length != 3 || parts[2].isBlank()) throw new IllegalArgumentException("JWT inválido");
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0])); JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        if (!header.path("alg").isTextual() || "none".equalsIgnoreCase(header.path("alg").asText())) throw new IllegalArgumentException("JWT sin firma");
        long exp = claims.path("exp").asLong(0); String sub = claims.path("sub").asText(); Instant now = clock.instant();
        if (exp == 0 || sub.isBlank() || !properties.clientId().equals(sub) || !Instant.ofEpochSecond(exp).isAfter(now)) throw new IllegalArgumentException("Claims JWT inválidos");
        return new ServiceAccessToken(value, sub, claims.has("iat") ? Instant.ofEpochSecond(claims.path("iat").asLong()) : null, Instant.ofEpochSecond(exp));
    }
}
