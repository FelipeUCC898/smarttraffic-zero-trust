package com.smarttraffic.query;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpTemplate;

class QueryClientTest {
    @Test void createsClientWithCorrelationCapableTemplate() { QueryClient client=new QueryClient(new ObjectMapper(),mock(AmqpTemplate.class)); assertTrue(client!=null); }
}
