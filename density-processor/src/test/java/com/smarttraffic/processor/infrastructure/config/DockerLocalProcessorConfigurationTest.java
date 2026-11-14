package com.smarttraffic.processor.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.processor.domain.model.CongestionThresholds;
import com.smarttraffic.processor.infrastructure.security.ServiceAccessTokenProvider;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class DockerLocalProcessorConfigurationTest {
    @Test void httpRequiresExplicitOptInAndSecureProfileStillRejectsIt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthClientProperties(URI.create("http://auth-server:8080/token"), "processor", "test-only", 1000));
        var auth = new AuthClientProperties(URI.create("http://auth-server:8080/token"), "processor", "test-only", 1000, true);
        assertThatIllegalArgumentException().isThrownBy(() -> new ProcessorRuntimeConfiguration()
                .processorTokens(auth, new ObjectMapper(), new AuthTlsProperties(null, null, null, null)));
    }
    @Test void localSettingsDoNotEnableTransactionsOrOAuthOnTheSingleBroker() {
        var configuration = new DockerLocalProcessorConfiguration(new MockEnvironment());
        var settings = new ProcessorRuntimeProperties("test-docker-local", "kafka:9092", "test-state", "test-zones", "test-pending",
                1000, 10, 1000, 5000, new CongestionThresholds(BigDecimal.ONE, BigDecimal.TEN), Map.of("north", BigDecimal.ONE),
                Map.of("sasl.mechanism", "OAUTHBEARER", "ssl.truststore.location", "unused"));
        var values = configuration.streamSettings(settings, mock(ServiceAccessTokenProvider.class));
        assertThat(values).containsEntry("security.protocol", "PLAINTEXT").containsEntry("processing.guarantee", "at_least_once")
                .containsEntry("auto.offset.reset", "latest")
                .doesNotContainKeys("sasl.mechanism", "ssl.truststore.location");
    }
    @Test void refusesMixingLocalAndSecureProfiles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DockerLocalProcessorConfiguration(
                new MockEnvironment().withProperty("spring.profiles.active", "processor")));
    }
    @Test void springBuildsLocalConnectionsAndAuthenticatesBeforeStartingStreams() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var probe = context.getBean(Probe.class);
            var order = inOrder(probe.tokens, probe.streams);
            order.verify(probe.tokens).obtainToken(); order.verify(probe.streams).start();
            assertThat(probe.config.originals()).containsEntry("security.protocol", "PLAINTEXT")
                    .containsEntry("processing.guarantee", "at_least_once");
            assertThat(probe.topology.describe().toString()).contains("raw_traffic_data", "test-pending");
        });
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Probe.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("docker-local"))
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withPropertyValues("traffic.processor.application-id=test-docker-local", "traffic.processor.bootstrap-servers=kafka:9092",
                        "traffic.processor.state-directory=test-state", "traffic.processor.zone-store=test-zones", "traffic.processor.pending-store=test-pending",
                        "traffic.processor.retry-interval-ms=1000", "traffic.processor.retry-batch-size=10", "traffic.processor.confirmation-timeout-ms=1000",
                        "traffic.processor.shutdown-timeout-ms=5000", "traffic.processor.thresholds.moderate-from=10",
                        "traffic.processor.thresholds.congested-from=20", "traffic.processor.zone-length-km.north=2",
                        "traffic.auth.token-endpoint=http://auth-server:8080/token", "traffic.auth.client-id=processor",
                        "traffic.auth.client-secret=test-only-secret", "traffic.auth.timeout-ms=1000", "traffic.auth.allow-insecure-http=true",
                        "spring.rabbitmq.host=rabbitmq", "spring.rabbitmq.username=test-user", "spring.rabbitmq.password=test-only",
                        "spring.rabbitmq.ssl.enabled=false");
    }
    @Configuration(proxyBeanMethods = false) @Profile("docker-local")
    static class Probe extends DockerLocalProcessorConfiguration {
        final KafkaStreams streams = mock(KafkaStreams.class);
        final ServiceAccessTokenProvider tokens = mock(ServiceAccessTokenProvider.class);
        Topology topology; StreamsConfig config;
        Probe(Environment environment) { super(environment); }
        @Bean @Override ServiceAccessTokenProvider processorTokens(AuthClientProperties properties, ObjectMapper mapper, AuthTlsProperties tls) { return tokens; }
        @Override protected KafkaStreams createStreams(Topology topology, StreamsConfig config) {
            this.topology = topology; this.config = config;
            when(streams.close(any(Duration.class))).thenReturn(true); return streams;
        }
    }
}
