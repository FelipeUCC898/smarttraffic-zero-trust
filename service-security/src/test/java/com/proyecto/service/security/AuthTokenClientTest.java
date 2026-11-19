package com.proyecto.service.security;

import com.proyecto.config.security.*;
import com.proyecto.model.security.*;
import com.proyecto.service.security.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthTokenClientTest {
    private final Instant now=Instant.parse("2026-11-16T12:00:00Z");
    private KeyPair key() throws Exception { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
    private AuthClientProperties properties(KeyPair key) {
        var p=new AuthClientProperties(); p.setTokenEndpoint(java.net.URI.create("https://localhost/token"));
        p.setClientId("query-client"); p.setClientSecret("test-only"); p.setIssuer("issuer"); p.setAudience("audience");
        p.setPublicKey("-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----");
        return p;
    }
    private String token(KeyPair key, String issuer, String audience, String subject, long expires) {
        return Jwts.builder().issuer(issuer).audience().add(audience).and().subject(subject)
                .issuedAt(Date.from(now.minusSeconds(10))).expiration(Date.from(now.plusSeconds(expires)))
                .signWith(key.getPrivate(),Jwts.SIG.RS256).compact();
    }
    @Test void verifiesSignatureIssuerAudienceSubjectAndExpiry() throws Exception {
        var key=key(); var client=new AuthTokenClient(properties(key),new ObjectMapper(),Clock.fixed(now,ZoneOffset.UTC));
        assertEquals("query-client",client.metadata(token(key,"issuer","audience","query-client",60)).principal());
        assertThrows(Exception.class,()->client.metadata(token(key(),"issuer","audience","query-client",60)));
        assertThrows(Exception.class,()->client.metadata(token(key,"wrong","audience","query-client",60)));
        assertThrows(Exception.class,()->client.metadata(token(key,"issuer","wrong","query-client",60)));
        assertThrows(Exception.class,()->client.metadata(token(key,"issuer","audience","other",60)));
        assertThrows(Exception.class,()->client.metadata(token(key,"issuer","audience","query-client",-1)));
    }
    @Test void rejectsHttpTokenEndpointBeforeSendingCredentials() throws Exception {
        var p=properties(key()); p.setTokenEndpoint(java.net.URI.create("http://localhost/token"));
        assertThrows(IllegalArgumentException.class,()->new AuthTokenClient(p,new ObjectMapper(),Clock.systemUTC()));
    }
}
