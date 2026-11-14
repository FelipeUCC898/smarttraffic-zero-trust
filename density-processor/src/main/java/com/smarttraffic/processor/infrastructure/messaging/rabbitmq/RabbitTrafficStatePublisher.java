package com.smarttraffic.processor.infrastructure.messaging.rabbitmq;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.processor.application.port.out.TrafficStatePublisher;
import com.smarttraffic.processor.domain.model.ZoneTrafficState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Adaptador aislado: no declara exchanges/colas ni inicia conexiones por sí solo. */
public final class RabbitTrafficStatePublisher implements TrafficStatePublisher {
    private static final String EXCHANGE = "traffic_updates";
    private final RabbitTemplate template;
    private final long confirmationTimeoutMs;
    private final ObjectWriter writer = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build().writerFor(ZoneTrafficState.class);

    /** Requiere un template dedicado, sin enrutadores de conexión ni cambios concurrentes. */
    public RabbitTrafficStatePublisher(RabbitTemplate template, long confirmationTimeoutMs) {
        if (template == null || confirmationTimeoutMs <= 0) {
            throw new IllegalArgumentException("template y tiempo positivo de confirmación son obligatorios");
        }
        this.template = template;
        this.confirmationTimeoutMs = confirmationTimeoutMs;
        validateConnectionFactory();
    }

    @Override
    public void publish(ZoneTrafficState state) {
        if (state == null) {
            throw new IllegalArgumentException("state es obligatorio");
        }
        validateConnectionFactory();
        Message message = toMessage(state);
        if (!Boolean.TRUE.equals(template.isMandatoryFor(message))) {
            throw new IllegalStateException("El template debe publicar con mandatory=true");
        }
        CorrelationData correlation = new CorrelationData();
        try {
            template.send(EXCHANGE, "", message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmationTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new TrafficStatePublicationException("RabbitMQ no confirmó la publicación");
            }
            if (correlation.getReturned() != null) {
                throw new TrafficStatePublicationException("RabbitMQ devolvió el estado sin ruta");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TrafficStatePublicationException("Espera de confirmación interrumpida; entrega incierta");
        } catch (TimeoutException exception) {
            throw new TrafficStatePublicationException("Confirmación agotada; entrega incierta");
        } catch (ExecutionException | AmqpException exception) {
            throw new TrafficStatePublicationException("Falló el envío o la confirmación del estado");
        }
    }

    private void validateConnectionFactory() {
        if (template.isChannelTransacted() || template.isUsePublisherConnection()) {
            throw new IllegalStateException("Se requiere un template no transaccional con conexión directa");
        }
        var factory = template.getConnectionFactory();
        if (factory == null || !factory.isPublisherConfirms() || !factory.isPublisherReturns()) {
            throw new IllegalStateException("La conexión requiere confirms correlacionados y publisher returns");
        }
    }

    private Message toMessage(ZoneTrafficState state) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return new Message(writer.writeValueAsBytes(state), properties);
        } catch (IOException | IllegalArgumentException exception) {
            throw new TrafficStatePublicationException("No se pudo representar el estado como JSON");
        }
    }
}
