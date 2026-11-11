package com.smarttraffic.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
class ZoneDashboard extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZoneDashboard.class);

    private final ObjectMapper mapper;
    private final Map<String, ZoneUpdate> latest = new HashMap<>();
    private final Set<WebSocketSession> sessions = new HashSet<>();

    ZoneDashboard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) throws IOException {
        for (ZoneUpdate update : latest.values()) {
            session.sendMessage(update.message());
        }
        sessions.add(session);
    }

    @Override
    public synchronized void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "${traffic.queue:traffic.dashboard.v1}", durable = "true"),
            exchange = @Exchange(value = "${traffic.exchange:traffic_updates}", type = "fanout")))
    synchronized void receive(byte[] body) {
        ZoneUpdate update;
        try {
            Event event = mapper.readValue(body, Event.class);
            if (event == null || event.zoneId() == null
                    || event.zoneId().isBlank() || event.zoneId().length() > 128 || event.measuredAt() == null
                    || event.density() == null || event.density().signum() < 0
                    || event.congestionState() == null || event.congestionState().isBlank()
                    || !Set.of("DESPEJADA", "MODERADA", "CONGESTIONADA").contains(event.congestionState())) {
                throw new IllegalArgumentException("Invalid zone state event");
            }
            update = new ZoneUpdate(event, new TextMessage(mapper.writeValueAsString(event)));
        } catch (IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid zone state event", exception);
        }

        Event event = update.event();
        ZoneUpdate current = latest.get(event.zoneId());
        if (current != null && !event.measuredAt().isAfter(current.event().measuredAt())) {
            return;
        }

        latest.put(event.zoneId(), update);
        for (WebSocketSession session : Set.copyOf(sessions)) {
            try {
                session.sendMessage(update.message());
            } catch (IOException | IllegalStateException exception) {
                sessions.remove(session);
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException closeFailure) {
                    LOGGER.debug("Could not close failed dashboard session", closeFailure);
                }
            }
        }
    }

    private record Event(String zoneId, Instant measuredAt,
                         BigDecimal density, String congestionState, String version) {}

    private record ZoneUpdate(Event event, TextMessage message) {}
}
