package com.smarttraffic.dashboard;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class ZoneDashboardTest {
    @Test
    void broadcastsFreshStateAndReplaysItToNewClients() throws Exception {
        ZoneDashboard dashboard = new ZoneDashboard(new ObjectMapper().findAndRegisterModules());
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);

        dashboard.afterConnectionEstablished(first);
        byte[] current = event("2026-01-02T00:00:00Z", 42);
        dashboard.receive(current);
        dashboard.receive(current);
        dashboard.receive(event("2026-01-01T00:00:00Z", 10));
        dashboard.afterConnectionEstablished(second);

        verify(first).sendMessage(argThat(message -> message.getPayload().toString().contains("\"density\":42")));
        verify(second).sendMessage(argThat(message -> message.getPayload().toString().contains("\"density\":42")));
        verifyNoMoreInteractions(first, second);
    }

    private byte[] event(String time, int density) {
        return """
                {"zoneId":"north","measuredAt":"%s","density":%d,"congestionState":"MODERADA","version":"v1"}
                """.formatted(time, density).getBytes(StandardCharsets.UTF_8);
    }
}
