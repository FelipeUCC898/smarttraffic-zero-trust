package com.smarttraffic.simulator.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.application.port.out.ServiceAccessTokenProvider;
import com.smarttraffic.simulator.application.port.out.TrafficObservationPublisher;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.infrastructure.messaging.kafka.KafkaTrafficObservationPublisher;
import com.smarttraffic.simulator.infrastructure.security.AuthServerTokenClient;
import com.smarttraffic.simulator.infrastructure.security.ServiceOAuthCallbackHandler;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("kafka")
@EnableScheduling
@EnableConfigurationProperties({SimulatorProperties.class, KafkaProperties.class,
        KafkaPublicationProperties.class, AuthClientProperties.class})
public class KafkaSimulatorConfiguration {
    public KafkaSimulatorConfiguration(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalArgumentException("Los perfiles local y kafka son mutuamente excluyentes");
        }
    }

    @Bean
    SimulateTrafficService simulateTrafficService(SimulatorProperties properties) {
        return new SimulateTrafficService(properties.sensors(), Clock.systemUTC(), UUID::randomUUID);
    }

    @Bean
    ServiceAccessTokenProvider serviceAccessTokenProvider(AuthClientProperties properties, ObjectMapper mapper) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new AuthServerTokenClient(properties, http, mapper, Clock.systemUTC());
    }

    @Bean
    DefaultKafkaProducerFactory<String, byte[]> simulatorProducerFactory(KafkaProperties properties,
            KafkaPublicationProperties publication, ServiceAccessTokenProvider tokens) {
        Map<String, Object> configs = properties.buildProducerProperties(null);
        configs.put("security.protocol", "SASL_SSL");
        configs.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
        configs.put(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
        configs.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;");
        configs.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, ServiceOAuthCallbackHandler.class);
        configs.put(ServiceOAuthCallbackHandler.TOKEN_PROVIDER_CONFIG, tokens);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configs.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, publication.publishTimeoutMs());
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    KafkaTemplate<String, byte[]> simulatorKafkaTemplate(DefaultKafkaProducerFactory<String, byte[]> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean
    TrafficObservationPublisher trafficObservationPublisher(KafkaTemplate<String, byte[]> kafka,
            ObjectMapper mapper, KafkaPublicationProperties publication) {
        return new KafkaTrafficObservationPublisher(kafka, mapper, publication.topic(), publication.publishTimeoutMs());
    }
}
