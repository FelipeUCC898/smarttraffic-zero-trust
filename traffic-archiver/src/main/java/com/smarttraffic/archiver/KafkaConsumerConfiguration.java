package com.smarttraffic.archiver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfiguration {
    @Bean
    DefaultErrorHandler trafficObservationErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
