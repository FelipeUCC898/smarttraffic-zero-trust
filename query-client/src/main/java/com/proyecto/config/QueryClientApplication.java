package com.proyecto.config;

import com.proyecto.config.security.RabbitOAuthConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;

@SpringBootApplication(scanBasePackages = "com.proyecto")
@Import(RabbitOAuthConfiguration.class)
public class QueryClientApplication { public static void main(String[] args) { SpringApplication.run(QueryClientApplication.class, args); } }

@org.springframework.context.annotation.Configuration
class QueryMessagingTopology {
    @Bean DirectExchange queryExchange() { return new DirectExchange("query_traffic", true, false); }
    @Bean TopicExchange answerExchange() { return new TopicExchange("query_answers", true, false); }
    @Bean Queue answerQueue() { return new AnonymousQueue(() -> "query_answers.query-client." + java.util.UUID.randomUUID()); }
    @Bean Binding answerBinding(Queue answerQueue, TopicExchange answerExchange) { return BindingBuilder.bind(answerQueue).to(answerExchange).with(answerQueue.getName()); }
}
