package com.smarttraffic.processor.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.infrastructure.security.ServiceAccessTokenProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;

class ProcessorRuntimeConfigurationTest {
    @Test void remainsInactiveWithoutProcessorProfile() {
        new ApplicationContextRunner().withUserConfiguration(ProcessorRuntimeConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(KafkaStreamsLifecycle.class));
    }
    @Test void springBuildsTheConnectedGraphAndStartsTheClientLifecycle() {
        configured().run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(Probe.class);
            verify(probe.streams).start();
            assertThat(probe.topology.describe().toString()).contains("test-zones", "test-pending", "raw_traffic_data");
            assertThat(context.getBean(KafkaStreamsLifecycle.class).isRunning()).isTrue();
            assertThat(context.getBean(CachingConnectionFactory.class).getRabbitConnectionFactory().getUsername())
                    .isEqualTo("test-client");
        });
    }
    @Test void refusesRabbitWithoutTlsRatherThanFallingBack() {
        configured().withPropertyValues("spring.rabbitmq.ssl.enabled=false").run(context -> assertThat(context).hasFailed());
    }
    @Test void kafkaSettingsCannotDisableTlsOauthOrTransactionalState() {
        var settings = new ProcessorRuntimeProperties("test-only", "unused.invalid:9092", "test-state", "test-zones", "test-pending",
                1000, 10, 1000, 5000, new CongestionThresholds(BigDecimal.ONE, BigDecimal.TEN), Map.of("north", BigDecimal.ONE),
                Map.of("security.protocol", "PLAINTEXT", "processing.guarantee", "at_least_once", "ssl.endpoint.identification.algorithm", ""));
        var config = ProcessorRuntimeConfiguration.kafkaProperties(settings, mock(ServiceAccessTokenProvider.class));
        assertThat(config).containsEntry("security.protocol", "SASL_SSL").containsEntry("sasl.mechanism", "OAUTHBEARER")
                .containsEntry("ssl.endpoint.identification.algorithm", "https")
                .containsEntry("processing.guarantee", "exactly_once_v2");
        assertThat(settings.toString()).doesNotContain("PLAINTEXT", "unused.invalid");
    }
    @Test void demoAndProductionProfilesCannotBeMixed() {
        configured().withInitializer(context -> context.getEnvironment().setActiveProfiles("processor", "processor-demo"))
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void demoRequiresItsOwnApplicationIdentity() {
        configured().withInitializer(context -> context.getEnvironment().setActiveProfiles("processor-demo"))
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void demoStartsWithADedicatedIdentityAndTheSameSecureConnections() {
        configured().withInitializer(context -> context.getEnvironment().setActiveProfiles("processor-demo"))
                .withPropertyValues("traffic.processor.application-id=test-only-runtime-demo")
                .run(context -> { assertThat(context).hasNotFailed(); verify(context.getBean(Probe.class).streams).start(); });
    }
    @Test void missingZoneLengthsPreventStartup() {
        configured().withPropertyValues("traffic.processor.zone-length-km.north=0")
                .run(context -> assertThat(context).hasFailed());
    }
    private ApplicationContextRunner configured() {
        return new ApplicationContextRunner().withUserConfiguration(Probe.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("processor"))
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withPropertyValues("traffic.processor.application-id=test-only-runtime", "traffic.processor.bootstrap-servers=unused.invalid:9092",
                        "traffic.processor.state-directory=test-state", "traffic.processor.zone-store=test-zones", "traffic.processor.pending-store=test-pending",
                        "traffic.processor.retry-interval-ms=1000", "traffic.processor.retry-batch-size=10", "traffic.processor.confirmation-timeout-ms=1000",
                        "traffic.processor.shutdown-timeout-ms=5000", "traffic.processor.thresholds.moderate-from=10",
                        "traffic.processor.thresholds.congested-from=20", "traffic.processor.zone-length-km.north=2",
                        "traffic.auth.token-endpoint=https://unused.invalid/token", "traffic.auth.client-id=test-client",
                        "traffic.auth.client-secret=test-only-secret", "traffic.auth.timeout-ms=1000",
                        "spring.rabbitmq.host=unused.invalid", "spring.rabbitmq.username=guest", "spring.rabbitmq.password=test-only-token",
                        "spring.rabbitmq.ssl.enabled=true", "spring.rabbitmq.ssl.validate-server-certificate=true", "spring.rabbitmq.ssl.verify-hostname=true");
    }
    @Configuration(proxyBeanMethods = false)
    @Profile({"processor", "processor-demo"})
    static class Probe extends ProcessorRuntimeConfiguration {
        final KafkaStreams streams = mock(KafkaStreams.class);
        Topology topology;
        @Override protected KafkaStreams createStreams(Topology topology, StreamsConfig config) {
            this.topology = topology;
            when(streams.close(any(Duration.class))).thenReturn(true);
            return streams;
        }
    }
}
