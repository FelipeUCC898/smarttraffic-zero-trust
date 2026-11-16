package com.smarttraffic.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traffic")
public class QueryClient {
    private final ObjectMapper mapper; private final Map<String, JsonNode> latest = new ConcurrentHashMap<>();
    public QueryClient(ObjectMapper mapper) { this.mapper=mapper; }
    @RabbitListener(bindings=@QueueBinding(value=@Queue(value="${traffic.query.input-queue:traffic.query-client.v1}", durable="true"), exchange=@Exchange(value="${traffic.exchange:traffic_updates}", type="fanout")))
    public void receive(byte[] body) throws Exception { JsonNode event=mapper.readTree(body); if (event!=null && event.path("zoneId").isTextual()) latest.put(event.path("zoneId").asText(), event); }
    @GetMapping("/{zoneId}") public JsonNode query(@PathVariable String zoneId) { return latest.getOrDefault(zoneId, mapper.createObjectNode().put("zoneId", zoneId).put("status", "NOT_FOUND")); }
}
