package com.smarttraffic.simulator.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.application.port.out.*;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.infrastructure.messaging.kafka.KafkaTrafficObservationPublisher;
import com.smarttraffic.simulator.infrastructure.security.*;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.kafka.core.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration(proxyBeanMethods = false)
@Profile("docker-local")
@EnableScheduling
@EnableConfigurationProperties({SimulatorProperties.class, KafkaProperties.class, KafkaPublicationProperties.class,
        AuthClientProperties.class, AuthTlsProperties.class})
public class DockerLocalSimulatorConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DockerLocalSimulatorConfiguration.class);
    public DockerLocalSimulatorConfiguration(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local", "kafka")))
            throw new IllegalArgumentException("docker-local no se mezcla con local o kafka");
        LOG.warn("DESARROLLO DOCKER: Auth HTTP y Kafka PLAINTEXT; no es Zero Trust");
    }
    @Bean SimulateTrafficService simulateTrafficService(SimulatorProperties properties) {
        return new SimulateTrafficService(properties.sensors(), Clock.systemUTC(), UUID::randomUUID);
    }
    @Bean ServiceAccessTokenProvider serviceAccessTokenProvider(AuthClientProperties properties, ObjectMapper mapper, AuthTlsProperties tls) {
        return new AuthServerTokenClient(properties, AuthHttpClientFactory.create(properties, tls), mapper, Clock.systemUTC());
    }
    @Bean DefaultKafkaProducerFactory<String, byte[]> simulatorProducerFactory(KafkaProperties properties, KafkaPublicationProperties publication) {
        Map<String, Object> config = properties.buildProducerProperties(null);
        config.keySet().removeIf(key -> key.startsWith("sasl.") || key.startsWith("ssl."));
        config.put("security.protocol", "PLAINTEXT");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, publication.publishTimeoutMs());
        return new DefaultKafkaProducerFactory<>(config);
    }
    @Bean KafkaTemplate<String, byte[]> simulatorKafkaTemplate(DefaultKafkaProducerFactory<String, byte[]> factory) {
        return new KafkaTemplate<>(factory);
    }
    @Bean TrafficObservationPublisher trafficObservationPublisher(KafkaTemplate<String, byte[]> kafka, ObjectMapper mapper,
            KafkaPublicationProperties publication, ServiceAccessTokenProvider tokens) {
        var publisher = new KafkaTrafficObservationPublisher(kafka, mapper, publication.topic(), publication.publishTimeoutMs());
        return reading -> { tokens.obtainToken(); publisher.publish(reading); };
    }
    @Bean @ConditionalOnProperty(name = "traffic.simulator.run-once", havingValue = "true")
    CommandLineRunner sendOneBatch(SimulateTrafficService simulator, TrafficObservationPublisher publisher,
            ConfigurableApplicationContext context) {
        return arguments -> {
            try {
                var batch = simulator.generateBatch();
                batch.forEach(publisher::publish);
                LOG.info("Lote local confirmado en Kafka: {} observaciones; finaliza el simulador", batch.size());
            } finally { context.close(); }
        };
    }
}
