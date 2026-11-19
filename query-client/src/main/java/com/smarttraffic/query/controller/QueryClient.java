package com.smarttraffic.query.controller;

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
    private final String replyQueue;
    private final long timeoutMs;
    public QueryClient(ObjectMapper mapper, AmqpTemplate rabbit, org.springframework.amqp.core.Queue answerQueue,
            @org.springframework.beans.factory.annotation.Value("${traffic.query.timeout-ms:2000}") long timeoutMs) {
        if(timeoutMs <= 0) throw new IllegalArgumentException("Positive timeout required");
        this.mapper=mapper; this.rabbit=rabbit; this.replyQueue=answerQueue.getName(); this.timeoutMs=timeoutMs;
    }
    @RabbitListener(queues="#{answerQueue.name}")
    public void receive(Message message) throws Exception {
        JsonNode response=mapper.readTree(message.getBody());
        String id=message.getMessageProperties().getCorrelationId();
        if(id==null || response==null || !id.equals(response.path("requestId").asText())) return;
        CompletableFuture<JsonNode> future=pending.remove(id); if(future!=null) future.complete(response);
    }
    public int pendingCount() { return pending.size(); }
    @GetMapping("/{zoneId}") public JsonNode query(@PathVariable String zoneId) {
        String requestId=UUID.randomUUID().toString(); String clientId="query-client"; CompletableFuture<JsonNode> future=new CompletableFuture<>(); pending.put(requestId,future);
        try { byte[] body=mapper.writeValueAsBytes(java.util.Map.of("requestId",requestId,"clientId",clientId,"originZone",zoneId,"destinationZone",zoneId,"requestedAt",java.time.Instant.now().toString())); MessageProperties p=new MessageProperties(); p.setContentType(MessageProperties.CONTENT_TYPE_JSON); p.setCorrelationId(requestId); p.setReplyTo(replyQueue); rabbit.send("query_traffic","query-client.query",new Message(body,p)); return future.orTimeout(timeoutMs,java.util.concurrent.TimeUnit.MILLISECONDS).join(); }
        catch (java.util.concurrent.CompletionException e) { throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"Query broker timeout"); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Query broker error"); }
        finally { pending.remove(requestId); }
    }
}
