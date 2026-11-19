package com.smarttraffic.query;

import com.smarttraffic.query.controller.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;

class QueryClientTest {
    @Test void replicasHaveDifferentExclusiveQueues() {
        var topology=new QueryMessagingTopology(); var a=topology.answerQueue(); var b=topology.answerQueue();
        org.junit.jupiter.api.Assertions.assertNotEquals(a.getName(),b.getName());
        assertTrue(a.isExclusive()); assertTrue(a.isAutoDelete());
    }
    @Test void correlatesReplyAndClearsPending() throws Exception {
        var mapper=new ObjectMapper(); var rabbit=mock(AmqpTemplate.class);
        var client=new QueryClient(mapper,rabbit,new QueryMessagingTopology().answerQueue(),100);
        org.mockito.Mockito.doAnswer(invocation->{
            org.springframework.amqp.core.Message request=invocation.getArgument(2);
            var props=new org.springframework.amqp.core.MessageProperties(); props.setCorrelationId(request.getMessageProperties().getCorrelationId());
            client.receive(new org.springframework.amqp.core.Message(mapper.writeValueAsBytes(java.util.Map.of("requestId",props.getCorrelationId(),"status","OK")),props));
            return null;
        }).when(rabbit).send(org.mockito.ArgumentMatchers.eq("query_traffic"),org.mockito.ArgumentMatchers.eq("query-client.query"),org.mockito.ArgumentMatchers.any(org.springframework.amqp.core.Message.class));
        org.junit.jupiter.api.Assertions.assertEquals("OK",client.query("z1").path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0,client.pendingCount());
    }
    @Test void timeoutClearsPending() {
        var client=new QueryClient(new ObjectMapper(),mock(AmqpTemplate.class),new QueryMessagingTopology().answerQueue(),10);
        var error=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->client.query("z1"));
        org.junit.jupiter.api.Assertions.assertEquals(504,error.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertEquals(0,client.pendingCount());
    }
}
