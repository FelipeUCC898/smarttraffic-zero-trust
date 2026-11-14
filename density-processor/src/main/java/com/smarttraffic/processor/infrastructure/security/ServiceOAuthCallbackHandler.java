package com.smarttraffic.processor.infrastructure.security;

import com.smarttraffic.processor.infrastructure.security.ServiceAccessToken;
import com.smarttraffic.processor.infrastructure.security.ServiceAccessTokenProvider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.SaslExtensions;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;

public final class ServiceOAuthCallbackHandler implements AuthenticateCallbackHandler {
    public static final String TOKEN_PROVIDER_CONFIG = "smarttraffic.auth.token-provider";
    private ServiceAccessTokenProvider provider;

    @Override
    public void configure(Map<String, ?> configs, String mechanism, List<AppConfigurationEntry> entries) {
        if (!"OAUTHBEARER".equals(mechanism)
                || !(configs.get(TOKEN_PROVIDER_CONFIG) instanceof ServiceAccessTokenProvider configured)) {
            throw new IllegalArgumentException("Se requiere OAUTHBEARER y proveedor explícito de tokens");
        }
        provider = configured;
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        if (provider == null) {
            throw new IOException("Callback de autenticación no configurado");
        }
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerTokenCallback tokenCallback) {
                try {
                    ServiceAccessToken token = provider.obtainToken();
                    tokenCallback.token(new OAuthBearerToken() {
                        @Override public String value() { return token.value(); }
                        @Override public Set<String> scope() { return Set.of(); }
                        @Override public long lifetimeMs() { return token.expiresAt().toEpochMilli(); }
                        @Override public String principalName() { return token.principal(); }
                        @Override public Long startTimeMs() {
                            return token.issuedAt() == null ? null : token.issuedAt().toEpochMilli();
                        }
                        @Override public String toString() { return "OAuthBearerToken[redacted]"; }
                    });
                } catch (RuntimeException exception) {
                    throw new IOException("No se pudo obtener la credencial de servicio");
                }
            } else if (callback instanceof SaslExtensionsCallback extensions) {
                extensions.extensions(new SaslExtensions(Map.of()));
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    @Override
    public void close() {
        provider = null;
    }
}
