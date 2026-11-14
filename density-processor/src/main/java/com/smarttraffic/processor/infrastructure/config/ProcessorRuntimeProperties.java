package com.smarttraffic.processor.infrastructure.config;

import com.smarttraffic.processor.domain.model.CongestionThresholds;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("traffic.processor")
public record ProcessorRuntimeProperties(String applicationId, String bootstrapServers, String stateDirectory,
        String zoneStore, String pendingStore, long retryIntervalMs, int retryBatchSize,
        long confirmationTimeoutMs, long shutdownTimeoutMs, CongestionThresholds thresholds,
        Map<String, BigDecimal> zoneLengthKm, Map<String, String> kafka) {
    public ProcessorRuntimeProperties {
        if (applicationId == null || !applicationId.matches("[a-zA-Z0-9._-]+") || applicationId.equals(".")
                || applicationId.equals("..") || bootstrapServers == null || bootstrapServers.isBlank()
                || stateDirectory == null || stateDirectory.isBlank() || zoneStore == null || pendingStore == null
                || !zoneStore.matches("[a-zA-Z0-9._-]+") || !pendingStore.matches("[a-zA-Z0-9._-]+")
                || zoneStore.equals(".") || zoneStore.equals("..") || pendingStore.equals(".") || pendingStore.equals("..")
                || zoneStore.equals(pendingStore) || retryIntervalMs < 1 || retryBatchSize < 1
                || confirmationTimeoutMs < 1 || shutdownTimeoutMs < 1 || thresholds == null
                || zoneLengthKm == null || zoneLengthKm.isEmpty()) {
            throw new IllegalArgumentException("La configuración de procesador está incompleta o no es válida");
        }
        zoneLengthKm = Map.copyOf(zoneLengthKm);
        kafka = kafka == null ? Map.of() : Map.copyOf(kafka);
    }

    @Override public String toString() { return "ProcessorRuntimeProperties[configuration=redacted]"; }
}
