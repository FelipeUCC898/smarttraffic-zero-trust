package com.smarttraffic.archiver.config;

import com.smarttraffic.archiver.config.*;
import com.smarttraffic.archiver.dto.*;
import com.smarttraffic.archiver.model.*;
import com.smarttraffic.archiver.repository.*;
import com.smarttraffic.archiver.service.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ArchiverSecureProfileTest {
    @Test
    void bindsComposePropertiesThroughTheSecureProfile() {
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("archiver-secure");
                    new ConfigDataApplicationContextInitializer().initialize(context);
                })
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "TRAFFIC_AUTH_TOKEN_ENDPOINT=https://auth.example.test/token",
                        "TRAFFIC_AUTH_CLIENT_ID=traffic-archiver",
                        "TRAFFIC_AUTH_CLIENT_SECRET=test-only",
                        "TRAFFIC_AUTH_TIMEOUT_MS=2500",
                        "TRAFFIC_AUTH_TLS_CA_CERTIFICATE=/run/secrets/auth-ca.pem")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var auth = context.getBean(AuthClientProperties.class);
                    assertThat(auth.tokenEndpoint()).isEqualTo(URI.create("https://auth.example.test/token"));
                    assertThat(auth.clientId()).isEqualTo("traffic-archiver");
                    assertThat(auth.clientSecret()).isEqualTo("test-only");
                    assertThat(auth.timeoutMs()).isEqualTo(2500);
                    assertThat(context.getBean(AuthTlsProperties.class).caCertificate())
                            .isEqualTo(Path.of("/run/secrets/auth-ca.pem"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AuthClientProperties.class, AuthTlsProperties.class})
    static class PropertiesConfiguration {
    }
}
