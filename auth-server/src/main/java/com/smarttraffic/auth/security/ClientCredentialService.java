package com.smarttraffic.auth.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class ClientCredentialService {
    private final Map<String, String> configuredSecrets;

    public ClientCredentialService(ClientCredentialProperties properties) {
        this.configuredSecrets = Map.copyOf(properties.getSecrets());
    }

    public boolean isValid(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return false;
        }
        String expected = configuredSecrets.get(clientId);
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                clientSecret.getBytes(StandardCharsets.UTF_8));
    }
}
