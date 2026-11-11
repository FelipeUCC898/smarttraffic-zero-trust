package com.smarttraffic.dashboard;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class WebSocketConfiguration implements WebSocketConfigurer {
    private final ZoneDashboard dashboard;

    WebSocketConfiguration(ZoneDashboard dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(dashboard, "/ws/zones");
    }
}
