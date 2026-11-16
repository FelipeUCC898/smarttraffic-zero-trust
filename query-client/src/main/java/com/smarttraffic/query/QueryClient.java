package com.smarttraffic.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traffic")
public class QueryClient {
    private final ObjectMapper mapper; private final AmqpTemplate rabbit; private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    public QueryClient(ObjectMapper mapper, AmqpTemplate rabbit) { this.mapper=mapper; this.rabbit=rabbit; }
    @RabbitListener(queues="${traffic.query.answers-queue:query_answers.query-client.v1}")
    public void receive(byte[] body) throws Exception { JsonNode response=mapper.readTree(body); String id=response.path("requestId").asText(); CompletableFuture<JsonNode> future=pending.remove(id); if(future!=null) future.complete(response); }
    @GetMapping("/{zoneId}") public JsonNode query(@PathVariable String zoneId) {
        String requestId=UUID.randomUUID().toString(); String clientId="query-client"; CompletableFuture<JsonNode> future=new CompletableFuture<>(); pending.put(requestId,future);
        try { byte[] body=mapper.writeValueAsBytes(java.util.Map.of("requestId",requestId,"clientId",clientId,"originZone",zoneId,"destinationZone",zoneId,"requestedAt",java.time.Instant.now().toString())); MessageProperties p=new MessageProperties(); p.setContentType(MessageProperties.CONTENT_TYPE_JSON); p.setCorrelationId(requestId); p.setReplyTo("query_answers.query-client.v1"); rabbit.send("query_traffic","query-client.query",new Message(body,p)); return future.orTimeout(2,java.util.concurrent.TimeUnit.SECONDS).join(); }
        catch (java.util.concurrent.CompletionException e) { throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"Query broker timeout"); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Query broker error"); }
        finally { pending.remove(requestId); }
    }
}
