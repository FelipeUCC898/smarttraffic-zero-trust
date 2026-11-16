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
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import io.jsonwebtoken.Jwts;

public final class AuthTokenClient implements ServiceAccessTokenProvider {
    private final AuthClientProperties properties; private final ObjectMapper mapper; private final HttpClient http; private final Clock clock;
    public AuthTokenClient(AuthClientProperties properties, ObjectMapper mapper, Clock clock) {
        properties.validate(); this.properties = properties; this.mapper = mapper; this.clock = clock; this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs())).build();
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
        if (properties.publicKey()==null || properties.publicKey().isBlank()) throw new IllegalArgumentException("Falta clave pública para validar JWT");
        PublicKey key=publicKey(properties.publicKey()); var parser=Jwts.parser().verifyWith(key);
        if(properties.issuer()!=null && !properties.issuer().isBlank()) parser.requireIssuer(properties.issuer());
        if(properties.audience()!=null && !properties.audience().isBlank()) parser.requireAudience(properties.audience());
        var claims=parser.build().parseSignedClaims(value).getPayload(); String sub=claims.getSubject(); Instant now=clock.instant();
        if(sub==null || !properties.clientId().equals(sub) || claims.getExpiration()==null || !claims.getExpiration().toInstant().isAfter(now)) throw new IllegalArgumentException("Claims JWT inválidos");
        return new ServiceAccessToken(value, sub, claims.getIssuedAt()==null?null:claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
    }
    private PublicKey publicKey(String value) throws Exception { String pem=Files.exists(Path.of(value))?Files.readString(Path.of(value)):value; byte[] bytes=Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s", "")); return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes)); }
}
