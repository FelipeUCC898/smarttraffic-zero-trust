package com.smarttraffic.query;

import com.smarttraffic.security.RabbitOAuthConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RabbitOAuthConfiguration.class)
public class QueryClientApplication { public static void main(String[] args) { SpringApplication.run(QueryClientApplication.class, args); } }
