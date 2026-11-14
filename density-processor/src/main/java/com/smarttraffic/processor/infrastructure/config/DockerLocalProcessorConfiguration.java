package com.smarttraffic.processor.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.processor.infrastructure.security.*;
import com.rabbitmq.client.impl.DefaultCredentialsRefreshService;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.boot.autoconfigure.amqp.RabbitConnectionFactoryBeanConfigurer;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Solo Docker de desarrollo: JWT ante Auth; los brokers locales no validan JWT. */
@Configuration(proxyBeanMethods = false)
@Profile("docker-local")
@EnableConfigurationProperties({ProcessorRuntimeProperties.class, AuthClientProperties.class, AuthTlsProperties.class, RabbitProperties.class})
public class DockerLocalProcessorConfiguration extends ProcessorRuntimeConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(DockerLocalProcessorConfiguration.class);

    public DockerLocalProcessorConfiguration(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("processor", "processor-demo")))
            throw new IllegalArgumentException("docker-local no se mezcla con perfiles seguros");
        LOG.warn("DESARROLLO DOCKER: datos simulados y brokers sin TLS/OAuth2; no es Zero Trust");
    }

    @Bean @Override
    ServiceAccessTokenProvider processorTokens(AuthClientProperties properties, ObjectMapper mapper, AuthTlsProperties tls) {
        return new AuthServerTokenClient(properties, AuthHttpClientFactory.create(properties, tls), mapper, Clock.systemUTC());
    }

    @Bean(destroyMethod = "destroy") @Override
    CachingConnectionFactory processorRabbitConnectionFactory(RabbitProperties properties, ResourceLoader loader,
            AuthClientProperties auth, ServiceAccessTokenProvider tokens, DefaultCredentialsRefreshService refreshService) throws Exception {
        if (properties.getSsl().determineEnabled() || properties.determineUsername() == null || properties.determineUsername().isBlank()
                || properties.determinePassword() == null || properties.determinePassword().isBlank())
            throw new IllegalArgumentException("docker-local requiere usuario/contraseña RabbitMQ explícitos y conexión local sin TLS");
        var bean = new RabbitConnectionFactoryBean();
        new RabbitConnectionFactoryBeanConfigurer(loader, properties).configure(bean);
        bean.afterPropertiesSet();
        var factory = new CachingConnectionFactory(bean.getObject());
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Override protected Map<String, Object> streamSettings(ProcessorRuntimeProperties properties, ServiceAccessTokenProvider tokens) {
        if (!properties.applicationId().endsWith("-docker-local"))
            throw new IllegalArgumentException("La aplicación local requiere sufijo -docker-local");
        Map<String, Object> config = new HashMap<>(properties.kafka());
        config.keySet().removeIf(key -> key.startsWith("sasl.") || key.startsWith("ssl."));
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, properties.applicationId());
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(StreamsConfig.STATE_DIR_CONFIG, properties.stateDirectory());
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.AT_LEAST_ONCE);
        config.put("security.protocol", "PLAINTEXT");
        config.put("auto.offset.reset", "latest");
        return config;
    }

    @Override protected Runnable beforeStreamsStart(ServiceAccessTokenProvider tokens) {
        return () -> {
            tokens.obtainToken();
            LOG.info("Identidad local validada por Auth; Kafka/RabbitMQ de desarrollo no autentican con ese JWT");
        };
    }
}
