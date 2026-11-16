package com.smarttraffic.alerts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AlertDispatcherTest {
    private final org.springframework.amqp.core.AmqpTemplate rabbit=org.mockito.Mockito.mock(org.springframework.amqp.core.AmqpTemplate.class);
    private final AlertDispatcher dispatcher=new AlertDispatcher(new ObjectMapper(),rabbit);
    @Test void ignoresNormalState() { dispatcher.dispatch("{\"zoneId\":\"z1\",\"congestionState\":\"MODERADA\"}".getBytes()); verify(rabbit,never()).send(eq("traffic_alerts"),any(String.class),any(org.springframework.amqp.core.Message.class)); }
    @Test void publishesCriticalState() { dispatcher.dispatch("{\"zoneId\":\"z1\",\"measuredAt\":\"2026-11-16T10:00:00Z\",\"congestionState\":\"CONGESTIONADA\"}".getBytes()); verify(rabbit).send(eq("traffic_alerts"),eq(""),any(org.springframework.amqp.core.Message.class)); }
}
