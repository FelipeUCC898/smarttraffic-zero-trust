package com.smarttraffic.auth.security;

import com.smarttraffic.auth.config.*;
import com.smarttraffic.auth.controller.*;
import com.smarttraffic.auth.model.*;
import com.smarttraffic.auth.service.*;

import com.smarttraffic.auth.controller.JwksController;
import io.jsonwebtoken.Jwts;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RsaJwtTest {
    @Test void signsRs256WithoutHmacAndPublishesUnsignedPublicModulus() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys=generator.generateKeyPair();
        String privatePem="-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----";
        String publicPem="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----";
        var service=new JwtService("not-a-base64-secret", "rs256",privatePem,900,"smarttraffic-auth","smarttraffic-brokers");
        var token=Jwts.parser().verifyWith(keys.getPublic()).requireIssuer("smarttraffic-auth").requireAudience("smarttraffic-brokers").build().parseSignedClaims(service.issue("traffic-dashboard"));
        assertEquals("RS256",token.getHeader().getAlgorithm());
        assertEquals("smarttraffic-rs256-v1",token.getHeader().getKeyId());
        assertEquals("traffic-dashboard",token.getPayload().getSubject());
        var jwk=(Map<?,?>)((List<?>)new JwksController(publicPem).keys().get("keys")).get(0);
        byte[] modulus=Base64.getUrlDecoder().decode((String)jwk.get("n"));
        assertNotEquals(0,modulus[0]);
        assertEquals(((RSAPublicKey)keys.getPublic()).getModulus(),new java.math.BigInteger(1,modulus));
        assertFalse(jwk.containsKey("d"));
    }
}
