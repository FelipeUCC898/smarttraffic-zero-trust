package com.smarttraffic.simulator.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.smarttraffic.simulator.infrastructure.security.ServiceOAuthCallbackHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.mock.env.MockEnvironment;

class KafkaConfigurationTest {
    @Test
    void enforcesAuthenticatedTlsAndConfirmationsWithoutCredentialsInJaas() {
        KafkaProperties properties = new KafkaProperties();
        properties.getProperties().putAll(Map.of("security.protocol", "PLAINTEXT",
                "ssl.endpoint.identification.algorithm", "", "sasl.mechanism", "PLAIN"));
        var configuration = new KafkaSimulatorConfiguration(new MockEnvironment());
        var factory = configuration.simulatorProducerFactory(properties,
                new KafkaPublicationProperties("raw_traffic_data", 30000), () -> { throw new IllegalStateException(); });
        try {
            Map<String, Object> configs = factory.getConfigurationProperties();
            assertThat(configs).containsEntry("security.protocol", "SASL_SSL")
                    .containsEntry("sasl.mechanism", "OAUTHBEARER")
                    .containsEntry("ssl.endpoint.identification.algorithm", "https")
                    .containsEntry("acks", "all").containsEntry("enable.idempotence", true)
                    .containsEntry("sasl.login.callback.handler.class", ServiceOAuthCallbackHandler.class);
            assertThat(configs.get("sasl.jaas.config").toString()).doesNotContain("unsecuredLogin", "clientSecret");
        } finally {
            factory.destroy();
        }
    }

    @Test
    void refusesSimultaneousLocalAndKafkaProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "kafka");

        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaSimulatorConfiguration(environment));
    }
}
