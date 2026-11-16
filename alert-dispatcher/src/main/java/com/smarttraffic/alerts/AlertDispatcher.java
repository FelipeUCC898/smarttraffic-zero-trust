package com.smarttraffic.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertDispatcher {
    private final ObjectMapper mapper; private final RabbitTemplate rabbit;
    public AlertDispatcher(ObjectMapper mapper, RabbitTemplate rabbit) { this.mapper=mapper; this.rabbit=rabbit; }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(value="${traffic.alerts.input-queue:traffic.alerts.dispatcher.v1}", durable="true"), exchange=@Exchange(value="${traffic.exchange:traffic_updates}", type="fanout")))
    public void dispatch(byte[] body) {
        try { JsonNode event=mapper.readTree(body); if (event==null || !event.path("zoneId").isTextual()) throw new IllegalArgumentException("Evento inválido");
            String state=event.path("congestionState").asText(); if (!"CONGESTIONADA".equals(state)) return;
            rabbit.send("traffic_alerts", "", new Message(mapper.writeValueAsBytes(Map.of("type","CONGESTION","zoneId",event.path("zoneId").asText(),"measuredAt",event.path("measuredAt").asText())), json()));
        } catch (Exception e) { throw new IllegalArgumentException("No se pudo despachar alerta", e); }
    }
    private MessageProperties json() { var p=new MessageProperties(); p.setContentType(MessageProperties.CONTENT_TYPE_JSON); p.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT); return p; }
}
