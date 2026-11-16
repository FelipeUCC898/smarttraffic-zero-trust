package com.smarttraffic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import io.jsonwebtoken.Jwts;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.net.ssl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SMARTTRAFFIC_RABBIT_TEST_DIR",matches=".+")
class AuthHttpsIntegrationTest {
    @Test void obtainsVerifiedTokenOverHttpsWithPrivateCa() throws Exception {
        Path directory=Path.of(System.getenv("SMARTTRAFFIC_RABBIT_TEST_DIR"));
        var store=KeyStore.getInstance("PKCS12");
        try(var in=Files.newInputStream(directory.resolve("server.p12"))) { store.load(in,"test-only".toCharArray()); }
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(store,"test-only".toCharArray());
        var ssl=SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(),null,null);
        var server=HttpsServer.create(new java.net.InetSocketAddress("localhost",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        String pem=Files.readString(directory.resolve("jwt-private.pem"));
        var key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s",""))));
        String jwt=Jwts.builder().subject("query-client").issuer("smarttraffic-auth").audience().add("smarttraffic-brokers").and().issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key,Jwts.SIG.RS256).compact();
        byte[] response=new ObjectMapper().writeValueAsBytes(Map.of("accessToken",jwt));
        server.createContext("/token",exchange->{
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            try(var output=exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var p=new AuthClientProperties(); p.setTokenEndpoint(java.net.URI.create("https://localhost:"+server.getAddress().getPort()+"/token"));
            p.setClientId("query-client"); p.setClientSecret("test-only"); p.setIssuer("smarttraffic-auth"); p.setAudience("smarttraffic-brokers");
            p.setPublicKey(directory.resolve("jwt-public.pem").toString()); p.setCaCertificate(directory.resolve("server.pem").toString());
            assertEquals("query-client",new AuthTokenClient(p,new ObjectMapper(),Clock.systemUTC()).obtainToken().principal());
            p.setCaCertificate(null);
            assertThrows(IllegalStateException.class,()->new AuthTokenClient(p,new ObjectMapper(),Clock.systemUTC()).obtainToken());
        } finally { server.stop(0); }
    }
}
