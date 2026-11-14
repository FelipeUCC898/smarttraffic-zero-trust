package com.smarttraffic.simulator.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.application.port.out.*;
import com.smarttraffic.simulator.application.service.SimulateTrafficService;
import com.smarttraffic.simulator.domain.model.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;

class DockerLocalSimulatorConfigurationTest {
    @ParameterizedTest @ValueSource(strings = {"local", "kafka"})
    void refusesMixingLocalDockerWithOtherModes(String profile) {
        var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
        assertThatIllegalArgumentException().isThrownBy(() -> new DockerLocalSimulatorConfiguration(environment));
    }
    @Test void httpRequiresOptInAndCannotBeUsedByTheSecureClient() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthClientProperties(URI.create("http://auth-server:8080/token"), "traffic-simulator", "test-only", 1000));
        var auth = new AuthClientProperties(URI.create("http://auth-server:8080/token"), "traffic-simulator", "test-only", 1000, true);
        assertThatIllegalArgumentException().isThrownBy(() -> new KafkaSimulatorConfiguration(new MockEnvironment())
                .serviceAccessTokenProvider(auth, new ObjectMapper(), new AuthTlsProperties(null, null, null, null)));
    }
    @Test void producerUsesDockerDnsAndPlaintextOnlyInThisConfiguration() {
        var properties = new KafkaProperties(); properties.setBootstrapServers(List.of("kafka:9092"));
        properties.getProperties().put("sasl.mechanism", "OAUTHBEARER");
        var factory = configuration().simulatorProducerFactory(properties, new KafkaPublicationProperties("raw_traffic_data", 1000));
        try {
            assertThat(factory.getConfigurationProperties()).containsEntry("security.protocol", "PLAINTEXT")
                    .containsEntry("bootstrap.servers", List.of("kafka:9092")).doesNotContainKey("sasl.mechanism");
        } finally { factory.destroy(); }
    }
    @Test void validatesServiceIdentityBeforeSendingToTheUnauthenticatedBroker() {
        var template = mock(KafkaTemplate.class);
        when(template.send(eq("raw_traffic_data"), eq("sensor-1"), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var tokens = mock(ServiceAccessTokenProvider.class);
        var publisher = configuration().trafficObservationPublisher(template, new ObjectMapper().findAndRegisterModules(),
                new KafkaPublicationProperties("raw_traffic_data", 1000), tokens);
        publisher.publish(reading());
        var order = inOrder(tokens, template);
        order.verify(tokens).obtainToken(); order.verify(template).send(eq("raw_traffic_data"), eq("sensor-1"), any(byte[].class));
    }
    @Test void oneShotSendsExactlyOneReadingAndClosesTheContext() throws Exception {
        var publisher = mock(TrafficObservationPublisher.class);
        var context = mock(ConfigurableApplicationContext.class);
        configuration().sendOneBatch(service(), publisher, context).run();
        verify(publisher).publish(any(SensorReading.class)); verifyNoMoreInteractions(publisher); verify(context).close();
    }
    @Test void oneShotDoesNotSwallowSendFailureAndStillReleasesTheContext() {
        var publisher = mock(TrafficObservationPublisher.class);
        var context = mock(ConfigurableApplicationContext.class);
        var failure = new IllegalStateException("Envío no confirmado"); doThrow(failure).when(publisher).publish(any());
        assertThatThrownBy(() -> configuration().sendOneBatch(service(), publisher, context).run()).isSameAs(failure);
        verify(context).close();
    }
    private DockerLocalSimulatorConfiguration configuration() { return new DockerLocalSimulatorConfiguration(new MockEnvironment()); }
    private SimulateTrafficService service() {
        return new SimulateTrafficService(List.of(new SensorScenario("sensor-1", "north",
                List.of(new MeasurementSample(42, new BigDecimal("35.5"), CongestionState.MODERADA)))),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), () -> new UUID(0, 1));
    }
    private SensorReading reading() {
        return new SensorReading(new UUID(0, 1), "sensor-1", "north", Instant.parse("2026-01-01T00:00:00Z"),
                42, new BigDecimal("35.5"), CongestionState.MODERADA);
    }
}
