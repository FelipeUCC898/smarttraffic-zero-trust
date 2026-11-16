package com.smarttraffic.alerts;

import com.smarttraffic.security.RabbitOAuthConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;

@SpringBootApplication
@Import(RabbitOAuthConfiguration.class)
public class AlertDispatcherApplication { public static void main(String[] args) { SpringApplication.run(AlertDispatcherApplication.class, args); } }

@org.springframework.context.annotation.Configuration
class AlertMessagingTopology {
    @Bean DirectExchange queryExchange() { return new DirectExchange("query_traffic", true, false); }
    @Bean TopicExchange answerExchange() { return new TopicExchange("query_answers", true, false); }
    @Bean FanoutExchange updatesExchange() { return new FanoutExchange("traffic_updates", true, false); }
    @Bean FanoutExchange alertsExchange() { return new FanoutExchange("traffic_alerts", true, false); }
    @Bean Queue queryQueue() { return QueueBuilder.durable("query_traffic_queue").build(); }
    @Bean Binding queryBinding(Queue queryQueue, DirectExchange queryExchange) { return BindingBuilder.bind(queryQueue).to(queryExchange).with("query-client.query"); }
    @Bean Queue updateQueue() { return QueueBuilder.durable("traffic.alerts.dispatcher.v1").build(); }
    @Bean Binding updateBinding(Queue updateQueue, FanoutExchange updatesExchange) { return BindingBuilder.bind(updateQueue).to(updatesExchange); }
}
