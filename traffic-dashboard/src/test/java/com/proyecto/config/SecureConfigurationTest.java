package com.proyecto.config;

import com.proyecto.service.ServiceAccessTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

class SecureConfigurationTest {
    @Test void wiresJwtFactoryAndRejectsPlaintextBroker() {
        var runner=new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class,JacksonAutoConfiguration.class))
                .withUserConfiguration(RabbitOAuthConfiguration.class)
                .withPropertyValues("spring.profiles.active=secure", "traffic.auth.token-endpoint=https://localhost/token",
                        "traffic.auth.client-id=traffic-dashboard", "traffic.auth.client-secret=test-only",
                        "traffic.auth.issuer=smarttraffic-auth", "traffic.auth.audience=smarttraffic-brokers",
                        "traffic.auth.public-key=test-public-key-file", "spring.rabbitmq.ssl.enabled=true",
                        "spring.rabbitmq.ssl.validate-server-certificate=true", "spring.rabbitmq.ssl.verify-hostname=true");
        runner.run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ServiceAccessTokenProvider.class);
            assertThat(context).hasBean("rabbitOAuthConnectionFactory");
        });
        runner.withPropertyValues("spring.rabbitmq.ssl.enabled=false").run(context->assertThat(context).hasFailed());
    }
}
