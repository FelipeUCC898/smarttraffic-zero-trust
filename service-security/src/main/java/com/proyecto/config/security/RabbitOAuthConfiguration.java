package com.proyecto.config.security;

import com.rabbitmq.client.impl.DefaultCredentialsRefreshService;
import com.proyecto.service.security.AuthTokenClient;
import com.proyecto.service.security.RabbitJwtCredentialsProvider;
import com.proyecto.service.security.ServiceAccessTokenProvider;
import java.time.Clock;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.boot.autoconfigure.amqp.RabbitConnectionFactoryBeanConfigurer;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@AutoConfigureBefore(RabbitAutoConfiguration.class)
@Profile("secure")
@EnableConfigurationProperties(AuthClientProperties.class)
public class RabbitOAuthConfiguration {
    @Bean ServiceAccessTokenProvider serviceAccessTokenProvider(AuthClientProperties p, com.fasterxml.jackson.databind.ObjectMapper mapper) { return new AuthTokenClient(p, mapper, Clock.systemUTC()); }
    @Bean DefaultCredentialsRefreshService rabbitCredentialRefresh() { return new DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder().build(); }
    @Bean @ConditionalOnMissingBean(CachingConnectionFactory.class)
    CachingConnectionFactory rabbitOAuthConnectionFactory(RabbitProperties properties, ResourceLoader loader, AuthClientProperties auth, ServiceAccessTokenProvider tokens, DefaultCredentialsRefreshService refresh) throws Exception {
        if (!Boolean.TRUE.equals(properties.getSsl().getEnabled()) || !properties.getSsl().isValidateServerCertificate() || !Boolean.TRUE.equals(properties.getSsl().getVerifyHostname())) throw new IllegalArgumentException("RabbitMQ secure requiere TLS verificado");
        var bean = new RabbitConnectionFactoryBean(); var configurer = new RabbitConnectionFactoryBeanConfigurer(loader, properties);
        configurer.setCredentialsProvider(new RabbitJwtCredentialsProvider(auth.clientId(), tokens, Clock.systemUTC())); configurer.setCredentialsRefreshService(refresh); configurer.configure(bean); bean.afterPropertiesSet();
        var factory = new CachingConnectionFactory(bean.getObject()); factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED); factory.setPublisherReturns(true); return factory;
    }
}
