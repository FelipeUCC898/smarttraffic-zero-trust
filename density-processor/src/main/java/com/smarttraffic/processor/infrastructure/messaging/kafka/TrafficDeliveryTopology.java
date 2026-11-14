package com.smarttraffic.processor.infrastructure.messaging.kafka;

import com.smarttraffic.processor.application.port.out.TrafficStatePublisher;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.Stores;

/** Conecta KTable y publicador mediante pendientes persistentes, sin tópicos externos nuevos. */
public final class TrafficDeliveryTopology {
    public Topology build(StreamsBuilder builder, TrafficProcessingTopology processing, TrafficStatePublisher publisher,
            String sourceTopic, String zoneStore, String pendingStore, Duration retryInterval, int batchSize) {
        if (builder == null || processing == null || publisher == null || retryInterval == null
                || retryInterval.toMillis() < 1 || batchSize < 1 || pendingStore == null
                || !pendingStore.matches("[a-zA-Z0-9._-]+") || pendingStore.equals(".") || pendingStore.equals("..")
                || pendingStore.equals(zoneStore)) {
            throw new IllegalArgumentException("Se requieren recursos distintos, intervalo y lote válidos");
        }
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(pendingStore),
                Serdes.String(), new ZoneTrafficSnapshotSerde()).withCachingDisabled());
        processing.build(builder, sourceTopic, zoneStore).toStream()
                .process(() -> new PendingTrafficStateProcessor(pendingStore, publisher, retryInterval, batchSize), pendingStore);
        return builder.build();
    }
}
