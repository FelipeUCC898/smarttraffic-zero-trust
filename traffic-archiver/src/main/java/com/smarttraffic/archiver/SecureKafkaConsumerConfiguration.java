package com.smarttraffic.archiver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.auth.SaslExtensions;
import org.apache.kafka.common.security.auth.SaslExtensionsCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

@Configuration
@Profile("secure-integration")
@EnableConfigurationProperties(SecureKafkaProperties.class)
public class SecureKafkaConsumerConfiguration {
    @Bean
    DefaultKafkaConsumerFactory<String, byte[]> secureConsumerFactory(KafkaProperties kafka,
            SecureKafkaProperties secure, ObjectMapper mapper) {
        Map<String, Object> props = new HashMap<>(kafka.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
        props.put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;");
        props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, TokenCallback.class);
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
        props.put("smarttraffic.auth.token-provider", new TokenProvider(secure, mapper,
                AuthHttpClientFactory.create(secure)));
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory(
            DefaultKafkaConsumerFactory<String, byte[]> factory) {
        var result = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        result.setConsumerFactory(factory);
        return result;
    }

    public static final class TokenCallback implements AuthenticateCallbackHandler {
        private TokenProvider provider;
        @Override public void configure(Map<String, ?> configs, String mechanism, List<AppConfigurationEntry> entries) {
            if (!"OAUTHBEARER".equals(mechanism) || !(configs.get("smarttraffic.auth.token-provider") instanceof TokenProvider value))
                throw new IllegalArgumentException("Se requiere proveedor OAuthBearer");
            provider = value;
        }
        @Override public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            if (provider == null) throw new IllegalStateException("Proveedor OAuthBearer no configurado");
            for (Callback callback : callbacks) {
                if (callback instanceof OAuthBearerTokenCallback target) {
                    Token token = provider.get();
                    target.token(new OAuthBearerToken() {
                        public String value() { return token.value; }
                        public Set<String> scope() { return Set.of(); }
                        public long lifetimeMs() { return token.expires.toEpochMilli(); }
                        public String principalName() { return token.subject; }
                        public Long startTimeMs() { return token.issued == null ? null : token.issued.toEpochMilli(); }
                    });
                } else if (callback instanceof SaslExtensionsCallback extensions) {
                    extensions.extensions(new SaslExtensions(Map.of()));
                } else throw new UnsupportedCallbackException(callback);
            }
        }
        @Override public void close() { provider = null; }
    }

    static final class TokenProvider {
        private final SecureKafkaProperties config; private final ObjectMapper mapper;
        private final HttpClient http;
        TokenProvider(SecureKafkaProperties config, ObjectMapper mapper, HttpClient http) {
            this.config = config; this.mapper = mapper; this.http = http;
        }
        Token get() {
            try {
                byte[] body = mapper.writeValueAsBytes(Map.of("clientId", config.clientId(), "clientSecret", config.clientSecret()));
                var request = HttpRequest.newBuilder(config.tokenEndpoint())
                        .timeout(java.time.Duration.ofMillis(config.timeoutMs()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) throw new IllegalStateException("Auth Server rechazó el cliente");
                String value = mapper.readTree(response.body()).path("accessToken").asText();
                String[] parts = value.split("\\.", -1);
                if (parts.length != 3) throw new IllegalStateException("JWT inválido");
                JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
                Instant expires = Instant.ofEpochSecond(claims.path("exp").asLong());
                Instant issued = claims.has("iat") ? Instant.ofEpochSecond(claims.path("iat").asLong()) : null;
                if (!config.clientId().equals(claims.path("sub").asText()) || !expires.isAfter(Instant.now()))
                    throw new IllegalStateException("JWT no corresponde al cliente");
                return new Token(value, claims.path("sub").asText(), issued, expires);
            } catch (Exception exception) { throw new IllegalStateException("No se pudo obtener el token OAuthBearer"); }
        }
    }
    record Token(String value, String subject, Instant issued, Instant expires) { }
}
