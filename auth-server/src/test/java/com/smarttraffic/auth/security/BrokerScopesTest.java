package com.smarttraffic.auth.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrokerScopesTest {
    @Test void kafkaOnlyClientsHaveNoRabbitPermissions() {
        assertTrue(BrokerScopes.forClient("traffic-simulator", "smarttraffic-brokers").isEmpty());
        assertTrue(BrokerScopes.forClient("traffic-archiver", "smarttraffic-brokers").isEmpty());
        assertTrue(BrokerScopes.forClient("unknown", "smarttraffic-brokers").isEmpty());
    }
    @Test void dashboardCannotPublishUpdatesOrReadQueries() {
        var scopes=BrokerScopes.forClient("traffic-dashboard", "smarttraffic-brokers");
        assertTrue(scopes.contains("smarttraffic-brokers.read:%2Fsmarttraffic/traffic.dashboard.v1"));
        assertFalse(scopes.contains("smarttraffic-brokers.write:%2Fsmarttraffic/traffic_updates"));
        assertTrue(scopes.stream().noneMatch(s->s.contains("query_")));
    }
}
