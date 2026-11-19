package com.proyecto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class SecureConfigurationTest {
    @Test void wiresJwtFactoryAndRejectsPlaintextBroker() {
        var runner=new ApplicationContextRunner()
                .withUserConfiguration(TestApplication.class)
                .withPropertyValues("spring.profiles.active=secure", "traffic.auth.token-endpoint=https://localhost/token",
                        "traffic.auth.client-id=traffic-dashboard", "traffic.auth.client-secret=test-only",
                        "traffic.auth.issuer=smarttraffic-auth", "traffic.auth.audience=smarttraffic-brokers",
                        "traffic.auth.public-key=test-public-key-file", "spring.rabbitmq.ssl.enabled=true",
                        "spring.rabbitmq.ssl.validate-server-certificate=true", "spring.rabbitmq.ssl.verify-hostname=true");
        runner.run(context->{
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("serviceAccessTokenProvider");
            assertThat(context).hasBean("rabbitOAuthConnectionFactory");
        });
        runner.withPropertyValues("spring.rabbitmq.ssl.enabled=false").run(context->assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
