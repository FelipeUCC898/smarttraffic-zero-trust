package com.smarttraffic.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlertDispatcher {
    private final java.util.concurrent.ConcurrentHashMap<String, JsonNode> latest = new java.util.concurrent.ConcurrentHashMap<>();
    private final ObjectMapper mapper; private final AmqpTemplate rabbit;
    public AlertDispatcher(ObjectMapper mapper, AmqpTemplate rabbit) { this.mapper=mapper; this.rabbit=rabbit; }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(value="${traffic.alerts.input-queue:traffic.alerts.dispatcher.v1}", durable="true"), exchange=@Exchange(value="${traffic.exchange:traffic_updates}", type="fanout")))
    public synchronized void dispatch(byte[] body) {
        try { JsonNode event=mapper.readTree(body); if (event==null || !event.path("zoneId").isTextual()) throw new IllegalArgumentException("Evento inválido");
            String state=event.path("congestionState").asText();
            if (!java.util.Set.of("DESPEJADA", "MODERADA", "CONGESTIONADA").contains(state)) throw new IllegalArgumentException("Estado inválido");
            String zone = event.path("zoneId").asText();
            if(zone.isBlank() || zone.length()>128) throw new IllegalArgumentException("Zona inválida");
            Instant measuredAt=Instant.parse(event.path("measuredAt").asText());
            JsonNode previous=latest.get(zone);
            if(previous!=null && !measuredAt.isAfter(Instant.parse(previous.path("measuredAt").asText()))) return;
            if("CONGESTIONADA".equals(state) && (previous==null || !"CONGESTIONADA".equals(previous.path("congestionState").asText()))) {
                rabbit.send("traffic_alerts", "", new Message(mapper.writeValueAsBytes(Map.of("alertId", UUID.randomUUID().toString(), "type","CONGESTION","zoneId",zone,"measuredAt",measuredAt.toString())), json()));
            }
            latest.put(zone, event.deepCopy());
        } catch (org.springframework.amqp.AmqpException e) { throw e;
        } catch (Exception e) { throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Evento de tráfico inválido", e); }
    }
    @RabbitListener(queues = "${traffic.query.queue:query_traffic_queue}")
    public void answerQuery(org.springframework.amqp.core.Message message) {
        try {
            JsonNode request=mapper.readTree(message.getBody());
            String correlation=message.getMessageProperties().getCorrelationId();
            String reply=message.getMessageProperties().getReplyTo();
            if(correlation==null || !correlation.equals(request.path("requestId").asText()) || reply==null || !reply.matches("query_answers\\.query-client\\.[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid reply metadata");
            String zone=request.path("originZone").asText();
            JsonNode snapshot=latest.get(zone);
            String destination=request.path("destinationZone").asText();
            boolean sameZone=zone.equals(destination);
            Map<String,Object> answer=new java.util.LinkedHashMap<>();
            answer.put("requestId",correlation);
            answer.put("clientId",request.path("clientId").asText());
            answer.put("status",snapshot==null?"NOT_FOUND":sameZone?"OK":"ROUTE_UNAVAILABLE");
            answer.put("path",snapshot!=null && sameZone?java.util.List.of(zone):java.util.List.of());
            answer.put("state",snapshot);
            answer.put("generatedAt",Instant.now().toString());
            // No road graph or travel-time model exists: never fabricate a route/ETA.
            MessageProperties properties=json(); properties.setCorrelationId(correlation);
            rabbit.send("query_answers",reply,new Message(mapper.writeValueAsBytes(answer),properties));
        } catch (org.springframework.amqp.AmqpException e) { throw e;
        } catch (Exception e) { throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Consulta inválida", e); }
    }
    private MessageProperties json() { var p=new MessageProperties(); p.setContentType(MessageProperties.CONTENT_TYPE_JSON); p.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT); return p; }
}
