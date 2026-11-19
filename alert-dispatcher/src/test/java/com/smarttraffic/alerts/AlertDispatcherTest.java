package com.proyecto;

import com.proyecto.service.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AlertDispatcherTest {
    @Test void queryReturnsObservedStateAndOriginalCorrelation() throws Exception {
        var mapper=new ObjectMapper();
        dispatcher.dispatch("{\"zoneId\":\"z1\",\"measuredAt\":\"2026-11-16T10:00:00Z\",\"congestionState\":\"MODERADA\"}".getBytes());
        var props=new org.springframework.amqp.core.MessageProperties(); props.setCorrelationId("request-1");
        String reply="query_answers.query-client."+java.util.UUID.randomUUID(); props.setReplyTo(reply);
        dispatcher.answerQuery(new org.springframework.amqp.core.Message("{\"requestId\":\"request-1\",\"clientId\":\"query-client\",\"originZone\":\"z1\",\"destinationZone\":\"z1\"}".getBytes(),props));
        var captured=org.mockito.ArgumentCaptor.forClass(org.springframework.amqp.core.Message.class);
        verify(rabbit).send(eq("query_answers"),eq(reply),captured.capture());
        var body=mapper.readTree(captured.getValue().getBody());
        org.junit.jupiter.api.Assertions.assertEquals("MODERADA",body.path("state").path("congestionState").asText());
        org.junit.jupiter.api.Assertions.assertEquals("request-1",captured.getValue().getMessageProperties().getCorrelationId());
        org.junit.jupiter.api.Assertions.assertFalse(body.has("estimatedMinutes"));
    }
    private final org.springframework.amqp.core.AmqpTemplate rabbit=org.mockito.Mockito.mock(org.springframework.amqp.core.AmqpTemplate.class);
    private final AlertDispatcher dispatcher=new AlertDispatcher(new ObjectMapper(),rabbit);
    @Test void ignoresNormalState() { dispatcher.dispatch("{\"zoneId\":\"z1\",\"measuredAt\":\"2026-11-16T10:00:00Z\",\"congestionState\":\"MODERADA\"}".getBytes()); verify(rabbit,never()).send(eq("traffic_alerts"),any(String.class),any(org.springframework.amqp.core.Message.class)); }
    @Test void duplicateCriticalEventDoesNotRepeatAlert() {
        byte[] event="{\"zoneId\":\"z1\",\"measuredAt\":\"2026-11-16T10:00:00Z\",\"congestionState\":\"CONGESTIONADA\"}".getBytes();
        dispatcher.dispatch(event); dispatcher.dispatch(event);
        verify(rabbit,org.mockito.Mockito.times(1)).send(eq("traffic_alerts"),eq(""),any(org.springframework.amqp.core.Message.class));
    }
    @Test void publishesCriticalState() { dispatcher.dispatch("{\"zoneId\":\"z1\",\"measuredAt\":\"2026-11-16T10:00:00Z\",\"congestionState\":\"CONGESTIONADA\"}".getBytes()); verify(rabbit).send(eq("traffic_alerts"),eq(""),any(org.springframework.amqp.core.Message.class)); }
}
