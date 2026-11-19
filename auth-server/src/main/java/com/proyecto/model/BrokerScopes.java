package com.proyecto.model;

import java.util.ArrayList;
import java.util.List;

/** Resource permissions for the /smarttraffic RabbitMQ vhost. Kafka uses ACLs. */
public final class BrokerScopes {
    private BrokerScopes() {}

    public static List<String> forClient(String client, String audience) {
        List<String> scopes = new ArrayList<>();
        switch (client) {
            case "processor" -> {
                grant(scopes, audience, "configure", "traffic_updates");
                grant(scopes, audience, "write", "traffic_updates");
            }
            case "traffic-dashboard" -> {
                grant(scopes, audience, "configure", "traffic_updates", "traffic.dashboard.v1");
                grant(scopes, audience, "read", "traffic_updates", "traffic.dashboard.v1");
                grant(scopes, audience, "write", "traffic.dashboard.v1");
            }
            case "alert-dispatcher" -> {
                grant(scopes, audience, "configure", "traffic_updates", "traffic_alerts", "traffic.alerts.dispatcher.v1", "query_traffic", "query_traffic_queue", "query_answers");
                grant(scopes, audience, "read", "traffic_updates", "traffic.alerts.dispatcher.v1", "query_traffic", "query_traffic_queue");
                grant(scopes, audience, "write", "traffic.alerts.dispatcher.v1", "query_traffic_queue", "traffic_alerts", "query_answers");
                grant(scopes, audience, "write", "query_answers/query_answers.query-client.*");
            }
            case "query-client" -> {
                grant(scopes, audience, "configure", "query_traffic", "query_answers", "query_answers.query-client.*");
                grant(scopes, audience, "read", "query_answers", "query_answers.query-client.*");
                grant(scopes, audience, "write", "query_traffic", "query_answers.query-client.*");
                grant(scopes, audience, "read", "query_answers/query_answers.query-client.*");
                grant(scopes, audience, "write", "query_answers.query-client.*/query_answers.query-client.*");
            }
            default -> { /* No RabbitMQ rights for Kafka-only or unknown identities. */ }
        }
        return List.copyOf(scopes);
    }

    private static void grant(List<String> scopes, String audience, String permission, String... resources) {
        for (String resource : resources) scopes.add(audience + "." + permission + ":%2Fsmarttraffic/" + resource);
    }
}
