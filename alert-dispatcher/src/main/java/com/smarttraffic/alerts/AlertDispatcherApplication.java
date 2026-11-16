package com.smarttraffic.alerts;

import com.smarttraffic.security.RabbitOAuthConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RabbitOAuthConfiguration.class)
public class AlertDispatcherApplication { public static void main(String[] args) { SpringApplication.run(AlertDispatcherApplication.class, args); } }
