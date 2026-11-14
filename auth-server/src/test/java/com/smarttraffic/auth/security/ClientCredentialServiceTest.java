package com.smarttraffic.auth.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCredentialServiceTest {
    private final ClientCredentialService service = service();

    private static ClientCredentialService service() {
        ClientCredentialProperties properties = new ClientCredentialProperties();
        properties.setSecrets(Map.of("traffic-simulator", "simulator-secret", "processor", "processor-secret"));
        return new ClientCredentialService(properties);
    }

    @Test
    void acceptsConfiguredClientSecret() {
        assertTrue(service.isValid("traffic-simulator", "simulator-secret"));
    }

    @Test
    void rejectsUnknownClientOrWrongSecret() {
        assertFalse(service.isValid("unknown", "simulator-secret"));
        assertFalse(service.isValid("traffic-simulator", "wrong-secret"));
    }

    @Test
    void rejectsBlankCredentials() {
        assertFalse(service.isValid("", "simulator-secret"));
        assertFalse(service.isValid("traffic-simulator", ""));
    }
}
