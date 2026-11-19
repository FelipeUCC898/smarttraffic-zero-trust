package com.proyecto.service;

import com.proyecto.config.*;
import com.proyecto.model.*;
import com.proyecto.service.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RabbitJwtCredentialsProviderTest {
    @Test void refreshesAndRedactsCredentials() {
        AtomicInteger calls=new AtomicInteger();
        var provider=new RabbitJwtCredentialsProvider("query-client", () -> { calls.incrementAndGet(); return new ServiceAccessToken("secret-token", "query-client", Instant.now(), Instant.now().plusSeconds(60)); }, Clock.systemUTC());
        assertEquals("query-client", provider.getUsername()); assertEquals("secret-token", provider.getPassword()); assertEquals(1,calls.get()); assertTrue(provider.toString().contains("redacted"));
    }
}
