package com.smarttraffic.processor.infrastructure.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

class RabbitJwtCredentialsProviderTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @Test void obtainsLazilyCachesAndRefreshesWithoutExposingSecrets() {
        var tokens = mock(ServiceAccessTokenProvider.class);
        when(tokens.obtainToken()).thenReturn(token("first-secret", "processor", NOW.plusSeconds(60)), token("second-secret", "processor", NOW.plusSeconds(120)));
        var provider = new RabbitJwtCredentialsProvider("processor", tokens, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(provider.getUsername()).isEqualTo("processor"); verifyNoInteractions(tokens);
        assertThat(provider.getPassword()).isEqualTo("first-secret");
        assertThat(provider.getTimeBeforeExpiration()).isEqualTo(Duration.ofSeconds(60));
        provider.refresh(); assertThat(provider.getPassword()).isEqualTo("second-secret");
        verify(tokens, times(2)).obtainToken();
        assertThat(provider.toString()).doesNotContain("first-secret", "second-secret");
    }
    @Test void rejectsExpiredOrDifferentIdentityTokens() {
        var tokens = mock(ServiceAccessTokenProvider.class);
        when(tokens.obtainToken()).thenReturn(token("secret", "other", NOW.plusSeconds(60)), token("secret", "processor", NOW));
        var provider = new RabbitJwtCredentialsProvider("processor", tokens, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(provider::getPassword).isInstanceOf(AmqpException.class);
        assertThatThrownBy(provider::getPassword).isInstanceOf(AmqpException.class);
    }
    @Test void sanitizesCredentialAcquisitionFailuresAsAmqpErrors() {
        var tokens = mock(ServiceAccessTokenProvider.class);
        when(tokens.obtainToken()).thenThrow(new IllegalStateException("private-auth-details"));
        var provider = new RabbitJwtCredentialsProvider("processor", tokens, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(provider::getPassword).isInstanceOf(AmqpException.class)
                .hasMessage("No se pudo renovar la credencial de servicio").hasNoCause();
    }
    @Test void requiresRealServiceIdentity() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RabbitJwtCredentialsProvider("guest", mock(ServiceAccessTokenProvider.class), Clock.systemUTC()));
    }
    private ServiceAccessToken token(String value, String principal, Instant expiry) {
        return new ServiceAccessToken(value, principal, NOW.minusSeconds(1), expiry);
    }
}
