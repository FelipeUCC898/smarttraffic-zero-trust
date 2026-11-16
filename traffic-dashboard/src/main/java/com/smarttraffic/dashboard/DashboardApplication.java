package com.smarttraffic.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import(com.smarttraffic.security.RabbitOAuthConfiguration.class)
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }
}
