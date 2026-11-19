package com.proyecto.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.proyecto")
@org.springframework.context.annotation.Import(com.proyecto.config.RabbitOAuthConfiguration.class)
public class DashboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DashboardApplication.class, args);
    }
}
