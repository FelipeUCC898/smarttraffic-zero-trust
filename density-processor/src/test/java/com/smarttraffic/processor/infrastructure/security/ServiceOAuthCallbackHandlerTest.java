package com.smarttraffic.processor.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smarttraffic.processor.infrastructure.security.ServiceAccessToken;
import com.smarttraffic.processor.infrastructure.security.ServiceAccessTokenProvider;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.junit.jupiter.api.Test;

class ServiceOAuthCallbackHandlerTest {
    @Test
    void providesBearerMetadataAndAcquiresANewTokenWhenKafkaRequestsRenewal() throws Exception {
        ServiceAccessTokenProvider provider = mock(ServiceAccessTokenProvider.class);
        var token = new ServiceAccessToken("only-a-test-token", "simulator-test",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:15:00Z"));
        when(provider.obtainToken()).thenReturn(token);
        ServiceOAuthCallbackHandler handler = handler(provider);
        OAuthBearerTokenCallback first = new OAuthBearerTokenCallback();
        SaslExtensionsCallback extensions = new SaslExtensionsCallback();

        handler.handle(new Callback[]{first, extensions});
        handler.handle(new Callback[]{new OAuthBearerTokenCallback()});

        assertThat(first.token().value()).isEqualTo(token.value());
        assertThat(first.token().principalName()).isEqualTo(token.principal());
        assertThat(first.token().lifetimeMs()).isEqualTo(token.expiresAt().toEpochMilli());
        assertThat(first.token().startTimeMs()).isEqualTo(token.issuedAt().toEpochMilli());
        assertThat(first.token().scope()).isEmpty();
        assertThat(first.token().toString()).doesNotContain(token.value());
        assertThat(extensions.extensions().map()).isEmpty();
        verify(provider, times(2)).obtainToken();
    }

    @Test
    void sanitizesProviderFailuresAndRefusesUnknownCallbacks() {
        ServiceOAuthCallbackHandler handler = handler(() -> { throw new IllegalStateException("sensitive-test-value"); });
        assertThatThrownBy(() -> handler.handle(new Callback[]{new OAuthBearerTokenCallback()}))
                .isInstanceOf(IOException.class).hasNoCause().hasMessage("No se pudo obtener la credencial de servicio");
        assertThatThrownBy(() -> handler.handle(new Callback[]{new NameCallback("name")}))
                .isInstanceOf(UnsupportedCallbackException.class);
    }

    @Test
    void requiresConfigurationAndRejectsOtherMechanisms() {
        ServiceOAuthCallbackHandler handler = new ServiceOAuthCallbackHandler();
        assertThatThrownBy(() -> handler.handle(new Callback[]{new OAuthBearerTokenCallback()})).isInstanceOf(IOException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> handler.configure(Map.of(), "PLAIN", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> handler.configure(Map.of(), "OAUTHBEARER", List.of()));
    }

    private ServiceOAuthCallbackHandler handler(ServiceAccessTokenProvider provider) {
        ServiceOAuthCallbackHandler handler = new ServiceOAuthCallbackHandler();
        handler.configure(Map.of(ServiceOAuthCallbackHandler.TOKEN_PROVIDER_CONFIG, provider), "OAUTHBEARER", List.of());
        return handler;
    }
}
