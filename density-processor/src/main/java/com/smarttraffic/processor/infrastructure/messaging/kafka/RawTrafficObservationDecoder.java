package com.smarttraffic.processor.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.smarttraffic.processor.domain.model.CongestionState;
import com.smarttraffic.processor.domain.model.TrafficObservation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Frontera JSON del tópico crudo; no abre conexiones ni confirma registros. */
public final class RawTrafficObservationDecoder {
    private final ObjectReader reader;

    public RawTrafficObservationDecoder(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper es obligatorio");
        }
        reader = mapper.copy().reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public TrafficObservation decode(String key, byte[] value) {
        if (key == null || key.isBlank()) {
            throw new InvalidTrafficObservationException("La clave sensorId es obligatoria");
        }
        TrafficObservation observation = decodeValue(value);
        if (!key.equals(observation.sensorId())) {
            throw new InvalidTrafficObservationException("La clave Kafka no coincide con sensorId");
        }
        return observation;
    }

    /** Valida el cuerpo; la frontera de entrada debe validar además la clave original. */
    public TrafficObservation decodeValue(byte[] value) {
        if (value == null || value.length == 0) {
            throw new InvalidTrafficObservationException("El cuerpo de la observación es obligatorio");
        }
        TrafficObservation observation;
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value)).toString();
            JsonNode root = reader.readTree(json);
            validateShape(root);
            // Leer del texto original evita perder precisión/escala decimal al pasar por un DoubleNode.
            observation = reader.forType(TrafficObservation.class).readValue(json);
            if (observation == null) {
                throw new IllegalArgumentException("Observación ausente");
            }
        } catch (IOException | IllegalArgumentException exception) {
            // No incluir el cuerpo no confiable ni fragmentos de parsing en los logs del consumidor.
            throw new InvalidTrafficObservationException("El mensaje no cumple el contrato de observación UTF-8 JSON");
        }
        return observation;
    }

    private void validateShape(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Se requiere un objeto JSON");
        }
        for (String field : List.of("eventId", "sensorId", "zoneId", "measuredAt", "congestionState")) {
            if (!root.path(field).isTextual()) {
                throw new IllegalArgumentException("Campo textual obligatorio ausente");
            }
        }
        String eventId = root.get("eventId").textValue();
        if (!UUID.fromString(eventId).toString().equalsIgnoreCase(eventId)) {
            throw new IllegalArgumentException("eventId debe ser UUID canónico");
        }
        CongestionState.valueOf(root.get("congestionState").textValue());
        if (!root.path("vehicleCount").isIntegralNumber() || !root.get("vehicleCount").canConvertToInt()
                || !root.path("averageSpeedKmh").isNumber()) {
            throw new IllegalArgumentException("Tipos de medición no válidos");
        }
    }
}
