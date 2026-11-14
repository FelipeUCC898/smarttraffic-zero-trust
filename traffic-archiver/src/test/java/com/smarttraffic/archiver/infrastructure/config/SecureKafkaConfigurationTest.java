package com.smarttraffic.archiver.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.archiver.infrastructure.security.ServiceAccessTokenProvider;
import com.smarttraffic.archiver.infrastructure.security.ServiceOAuthCallbackHandler;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

class SecureKafkaConfigurationTest {
    @Test
    void configuresOAuthAndPreservesExistingConsumerProperties() {
        var factory = new DefaultKafkaConsumerFactory<>(Map.of("group.id", "archive-test"));
        var auth = new AuthClientProperties(URI.create("https://auth.example.test/token"),
                "traffic-archiver", "test-only", 1000);
        var tls = new AuthTlsProperties(null, null, null, null);

        new SecureKafkaConfiguration().archiverOAuth(auth, tls, new ObjectMapper()).customize(factory);

        assertThat(factory.getConfigurationProperties())
                .containsEntry("security.protocol", "SASL_SSL")
                .containsEntry("ssl.endpoint.identification.algorithm", "https")
                .containsEntry("sasl.mechanism", "OAUTHBEARER")
                .containsEntry("sasl.login.callback.handler.class", ServiceOAuthCallbackHandler.class)
                .containsEntry("group.id", "archive-test");
        assertThat(factory.getConfigurationProperties().get(ServiceOAuthCallbackHandler.TOKEN_PROVIDER_CONFIG))
                .isInstanceOf(ServiceAccessTokenProvider.class);
    }
}
