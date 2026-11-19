package com.proyecto.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.service.security.AuthHttpClientFactory;
import com.proyecto.service.security.AuthServerTokenClient;
import com.proyecto.service.security.ServiceOAuthCallbackHandler;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("archiver-secure")
@EnableConfigurationProperties({AuthClientProperties.class, AuthTlsProperties.class})
class SecureKafkaConfiguration {
    @Bean
    DefaultKafkaConsumerFactoryCustomizer archiverOAuth(
            AuthClientProperties auth, AuthTlsProperties tls, ObjectMapper mapper) {
        var provider = new AuthServerTokenClient(auth, AuthHttpClientFactory.create(auth, tls),
                mapper, Clock.systemUTC());
        return factory -> factory.updateConfigs(Map.of(
                "security.protocol", "SASL_SSL",
                "ssl.endpoint.identification.algorithm", "https",
                "sasl.mechanism", "OAUTHBEARER",
                "sasl.jaas.config", "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;",
                "sasl.login.callback.handler.class", ServiceOAuthCallbackHandler.class,
                ServiceOAuthCallbackHandler.TOKEN_PROVIDER_CONFIG, provider));
    }
}
