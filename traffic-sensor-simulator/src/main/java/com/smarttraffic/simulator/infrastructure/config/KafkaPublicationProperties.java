package com.smarttraffic.simulator.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "traffic.kafka")
public record KafkaPublicationProperties(String topic, long publishTimeoutMs) {
    public KafkaPublicationProperties {
        if (topic == null || topic.isBlank() || publishTimeoutMs < 1000) {
            throw new IllegalArgumentException("topic es obligatorio y publish-timeout-ms debe ser al menos 1000");
        }
    }
}
