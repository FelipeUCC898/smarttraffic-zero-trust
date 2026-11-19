package com.smarttraffic.auth.controller;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {
    private final String publicKey;
    public JwksController(@Value("${security.jwt.public-key:}") String publicKey) { this.publicKey=publicKey; }
    @GetMapping("/.well-known/jwks.json") public Map<String,Object> keys() { if(publicKey.isBlank()) return Map.of("keys", java.util.List.of()); try { String pem=publicKey.contains("-----BEGIN")?publicKey:Files.readString(Path.of(publicKey)); byte[] der=Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s", "")); RSAPublicKey key=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der)); return Map.of("keys", java.util.List.of(Map.of("kid","smarttraffic-rs256-v1","kty","RSA","alg","RS256","use","sig","n",b64(key.getModulus().toByteArray()),"e",b64(key.getPublicExponent().toByteArray())))); } catch(Exception e){ throw new IllegalStateException("JWKS no disponible"); } }
    private String b64(byte[] bytes){ if(bytes.length > 1 && bytes[0] == 0) bytes=java.util.Arrays.copyOfRange(bytes,1,bytes.length); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
