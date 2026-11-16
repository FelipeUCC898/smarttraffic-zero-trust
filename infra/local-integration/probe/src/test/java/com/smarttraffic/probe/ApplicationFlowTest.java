package com.smarttraffic.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.apache.kafka.clients.producer.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Local fixtures only. No AWS endpoint, deployment key or production permission. */
@EnabledIfEnvironmentVariable(named="SMARTTRAFFIC_APPLICATION_FLOW", matches="true")
class ApplicationFlowTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static SSLContext trust() throws Exception {
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of("/fixtures/truststore.p12"))) {
            store.load(input, System.getenv("TRUSTSTORE_PASSWORD").toCharArray());
        }
        var manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        manager.init(store);
        var context = SSLContext.getInstance("TLS");
        context.init(null, manager.getTrustManagers(), null);
        return context;
    }

    private static Instant publish(int vehicles) throws Exception {
        BrokerSecurityTest.currentToken = BrokerSecurityTest.valid("traffic-simulator");
        Instant measured = Instant.now();
        String payload = JSON.writeValueAsString(Map.of("eventId", UUID.randomUUID().toString(),
            "sensorId", "application-probe", "zoneId", "north", "measuredAt", measured.toString(),
            "vehicleCount", vehicles, "averageSpeedKmh", 21,
            "congestionState", vehicles == 0 ? "DESPEJADA" : "CONGESTIONADA"));
        try (var producer = new KafkaProducer<String, String>(BrokerSecurityTest.config("kafka", true))) {
            producer.send(new ProducerRecord<>("raw_traffic_data", "application-probe", payload)).get(45, TimeUnit.SECONDS);
        }
        return measured;
    }

    private static JsonNode awaitState(Instant measured, String state) throws Exception {
        Instant deadline = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(deadline)) {
            var response = HTTP.send(HttpRequest.newBuilder(URI.create("http://query:8092/api/traffic/north"))
                .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode answer = JSON.readTree(response.body());
                if ("OK".equals(answer.path("status").asText()) &&
                    state.equals(answer.path("state").path("congestionState").asText()) &&
                    !Instant.parse(answer.path("state").path("measuredAt").asText()).isBefore(measured)) {
                    UUID.fromString(answer.path("requestId").asText());
                    return answer;
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Fresh query state not received: " + state);
    }

    @Test void webSocketAlertAndFreshQuerySurviveTokenExpiry() throws Exception {
        BrokerSecurityTest.loadKey();
        // Fail if the short-lived issuer configuration was accidentally removed.
        var secureHttp = HttpClient.newBuilder().sslContext(trust()).connectTimeout(Duration.ofSeconds(5)).build();
        var tokenResponse = secureHttp.send(HttpRequest.newBuilder(URI.create("https://auth-server:8443/token"))
            .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("clientId", "traffic-simulator",
                "clientSecret", System.getenv("AUTH_CLIENT_TRAFFIC_SIMULATOR_SECRET"))))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, tokenResponse.statusCode());
        String jwt = JSON.readTree(tokenResponse.body()).path("accessToken").asText();
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        long lifetime = claims.path("exp").asLong() - claims.path("iat").asLong();
        assertTrue(lifetime > 0 && lifetime <= 60, "Local renewal probe requires <=60s issuer tokens");

        String queue = "probe.alert." + UUID.randomUUID();
        // Ephemeral observer has rights only to its own queue and the alert exchange.
        // It signs using disposable LOCAL fixtures and is not an application identity.
        String scope = "smarttraffic-brokers.configure:%2Fsmarttraffic/" + queue +
            " smarttraffic-brokers.read:%2Fsmarttraffic/" + queue +
            " smarttraffic-brokers.write:%2Fsmarttraffic/" + queue +
            " smarttraffic-brokers.read:%2Fsmarttraffic/traffic_alerts";
        String observer = Jwts.builder().header().keyId("smarttraffic-rs256-v1").and()
            .subject("local-alert-observer").issuer("smarttraffic-auth").audience().add("smarttraffic-brokers").and()
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300))).claim("scope", scope)
            .signWith(BrokerSecurityTest.key, Jwts.SIG.RS256).compact();
        var factory = new ConnectionFactory();
        factory.setHost("rabbitmq"); factory.setPort(5671); factory.setVirtualHost("/smarttraffic");
        factory.setUsername("local-alert-observer"); factory.setPassword(observer);
        factory.setConnectionTimeout(5000); factory.setHandshakeTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false); factory.useSslProtocol(trust()); factory.enableHostnameVerification();
        var messages = new LinkedBlockingQueue<String>();
        var socket = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
            .buildAsync(URI.create("ws://dashboard:8081/ws/zones"), new WebSocket.Listener() {
                final StringBuilder fragments = new StringBuilder();
                public void onOpen(WebSocket ws) { ws.request(1); }
                public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
                    fragments.append(text);
                    if (last) { messages.add(fragments.toString()); fragments.setLength(0); }
                    ws.request(1); return null;
                }
            }).get(10, TimeUnit.SECONDS);
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.queueDeclare(queue, false, true, true, null);
            channel.queueBind(queue, "traffic_alerts", "");
            var alerts = new LinkedBlockingQueue<String>();
            channel.basicConsume(queue, true, (tag, delivery) -> alerts.add(new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8)), tag -> {});
            Instant clear = publish(0);
            String firstRequest = awaitState(clear, "DESPEJADA").path("requestId").asText();
            // Cross the actual issuer lifetime while real application connections run.
            Thread.sleep((lifetime + 10) * 1000);
            Instant congested = publish(42);
            JsonNode answer = awaitState(congested, "CONGESTIONADA");
            assertNotEquals(firstRequest, answer.path("requestId").asText());
            String alert = alerts.poll(45, TimeUnit.SECONDS);
            assertNotNull(alert, "Dispatcher alert was not delivered to a real RabbitMQ consumer");
            JsonNode event = JSON.readTree(alert);
            assertEquals("CONGESTION", event.path("type").asText());
            assertEquals("north", event.path("zoneId").asText());
            assertFalse(Instant.parse(event.path("measuredAt").asText()).isBefore(congested));
            boolean websocketReceived = false;
            Instant deadline = Instant.now().plusSeconds(45);
            while (Instant.now().isBefore(deadline)) {
                String message = messages.poll(1, TimeUnit.SECONDS);
                if (message == null) continue;
                JsonNode update = JSON.readTree(message);
                if ("CONGESTIONADA".equals(update.path("congestionState").asText()) &&
                    !Instant.parse(update.path("measuredAt").asText()).isBefore(congested)) { websocketReceived = true; break; }
            }
            assertTrue(websocketReceived, "Dashboard did not deliver fresh state over WebSocket");
            Properties props = new Properties(); props.put("user", "smarttraffic"); props.put("password", System.getenv("POSTGRES_PASSWORD"));
            try (var db = DriverManager.getConnection("jdbc:postgresql://postgres/smarttraffic?sslmode=verify-full&sslrootcert=/fixtures/ca.pem", props);
                 var statement = db.prepareStatement("SELECT count(*) FROM traffic_observation WHERE sensor_id='application-probe' AND measured_at >= ? AND vehicle_count=42")) {
                statement.setObject(1, congested.atOffset(ZoneOffset.UTC));
                int count = 0;
                for (int i=0; i<30; i++) {
                    try (var result = statement.executeQuery()) { result.next(); count = result.getInt(1); }
                    if (count > 0) break;
                    Thread.sleep(500);
                }
                assertTrue(count > 0, "Archiver did not persist the post-expiry observation");
            }
            System.out.println("PASS: real WebSocket, delivered congestion alert, correlated fresh query and TLS persistence after short-lived issuer token expiry.");
        } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "probe complete").get(5, TimeUnit.SECONDS); }
    }
}
