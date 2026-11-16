package com.smarttraffic.query;

import com.smarttraffic.security.RabbitOAuthConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;

@SpringBootApplication
@Import(RabbitOAuthConfiguration.class)
public class QueryClientApplication { public static void main(String[] args) { SpringApplication.run(QueryClientApplication.class, args); } }

@org.springframework.context.annotation.Configuration
class QueryMessagingTopology {
    @Bean DirectExchange queryExchange() { return new DirectExchange("query_traffic", true, false); }
    @Bean DirectExchange answerExchange() { return new DirectExchange("query_answers", true, false); }
    @Bean Queue answerQueue() { return QueueBuilder.durable("query_answers.query-client.v1").build(); }
    @Bean Binding answerBinding(Queue answerQueue, DirectExchange answerExchange) { return BindingBuilder.bind(answerQueue).to(answerExchange).with("query-client.answer"); }
}
