package com.smarttraffic.auth.service;

import com.smarttraffic.auth.model.BrokerScopes;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey signingKey; private final PrivateKey privateKey; private final long expirationSeconds; private final String mode; private final String issuer; private final String audience;

    @Autowired
    public JwtService(
            @Value("${security.jwt.secret:}") String secret, @Value("${security.jwt.mode:hmac}") String mode,
            @Value("${security.jwt.private-key:}") String privateKey, @Value("${security.jwt.expiration-seconds:900}") long expirationSeconds,
            @Value("${security.jwt.issuer:smarttraffic-auth}") String issuer, @Value("${security.jwt.audience:smarttraffic-brokers}") String audience) {
        if (!"hmac".equalsIgnoreCase(mode) && !"rs256".equalsIgnoreCase(mode)) throw new IllegalArgumentException("Unsupported JWT mode");
        this.mode=mode; this.issuer=issuer; this.audience=audience; this.signingKey="hmac".equalsIgnoreCase(mode)?Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)):null; this.privateKey="rs256".equalsIgnoreCase(mode)?load(privateKey):null; this.expirationSeconds = expirationSeconds;
        if("rs256".equalsIgnoreCase(mode) && this.privateKey==null) throw new IllegalArgumentException("El perfil RS256 requiere una clave privada externa");
    }

    public JwtService(String secret, long expirationSeconds) { this(secret, "hmac", "", expirationSeconds, "smarttraffic-auth", "smarttraffic-brokers"); }

    public String issue(String clientId) {
        Instant now = Instant.now();
        var builder=Jwts.builder()
                .subject(clientId)
                .issuer(issuer).audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .claim("roles", java.util.List.of("SERVICE")).claim("scope", BrokerScopes.forClient(clientId, audience));
        return "rs256".equalsIgnoreCase(mode)?builder.header().keyId("smarttraffic-rs256-v1").and().signWith(privateKey, Jwts.SIG.RS256).compact():builder.signWith(signingKey).compact();
    }
    private PrivateKey load(String configured) { try { String pem=configured.contains("BEGIN")?configured:Files.readString(Path.of(configured)); byte[] bytes=Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s", "")); return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes)); } catch(Exception e){ throw new IllegalArgumentException("No se pudo cargar la clave privada RS256 externa"); } }
}
