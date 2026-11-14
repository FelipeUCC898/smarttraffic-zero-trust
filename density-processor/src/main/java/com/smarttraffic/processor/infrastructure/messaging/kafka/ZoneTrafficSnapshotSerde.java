package com.smarttraffic.processor.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.processor.domain.model.ZoneTrafficSnapshot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Serde de valores de estado interno, no del tópico crudo ni de RabbitMQ.
 * No crea clientes Kafka, almacenes o tópicos. No aplica migraciones implícitas.
 */
public final class ZoneTrafficSnapshotSerde implements Serde<ZoneTrafficSnapshot> {
    private static final int FORMAT_VERSION = 1;
    private final ObjectReader reader;
    private final ObjectWriter writer;
    private final Serializer<ZoneTrafficSnapshot> serializer = this::serialize;
    private final Deserializer<ZoneTrafficSnapshot> deserializer = this::deserialize;

    public ZoneTrafficSnapshotSerde() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        reader = mapper.reader();
        writer = mapper.writerFor(Envelope.class);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (isKey) {
            throw new IllegalArgumentException("La instantánea es un valor de estado, no una clave");
        }
    }

    @Override
    public Serializer<ZoneTrafficSnapshot> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<ZoneTrafficSnapshot> deserializer() {
        return deserializer;
    }

    private byte[] serialize(String topic, ZoneTrafficSnapshot snapshot) {
        if (snapshot == null) {
            return null; // Convención de tombstone de Kafka; no autoriza borrados del dominio.
        }
        try {
            return writer.writeValueAsBytes(new Envelope(FORMAT_VERSION, snapshot));
        } catch (IOException | IllegalArgumentException exception) {
            throw new SerializationException("No se pudo serializar la instantánea de zona");
        }
    }

    private ZoneTrafficSnapshot deserialize(String topic, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root = reader.readTree(json);
            if (root == null || !root.isObject() || !root.path("formatVersion").isIntegralNumber()
                    || !root.get("formatVersion").canConvertToInt()
                    || root.get("formatVersion").intValue() != FORMAT_VERSION
                    || !root.path("snapshot").isObject()) {
                throw new IllegalArgumentException("Formato interno no compatible");
            }
            JsonNode snapshot = root.get("snapshot");
            for (String field : new String[] {"latestObservation", "latestState"}) {
                JsonNode value = snapshot.path(field);
                if (!value.isMissingNode() && !value.isNull() && !value.path("measuredAt").isTextual()) {
                    throw new IllegalArgumentException("La fecha debe ser textual");
                }
            }
            // Leer el original conserva precisión y escala BigDecimal, sin pasar por double.
            Envelope envelope = reader.forType(Envelope.class).readValue(json);
            return envelope.snapshot();
        } catch (IOException | IllegalArgumentException exception) {
            // No registrar bytes, identificadores o fragmentos del estado corrupto.
            throw new SerializationException("Instantánea de zona inválida o formato no compatible");
        }
    }

    private record Envelope(int formatVersion, ZoneTrafficSnapshot snapshot) {
    }
}
