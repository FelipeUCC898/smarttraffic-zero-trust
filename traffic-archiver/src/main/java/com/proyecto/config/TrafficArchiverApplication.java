package com.proyecto.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.proyecto")
@EntityScan("com.proyecto.model")
@EnableJpaRepositories("com.proyecto.repository")
public class TrafficArchiverApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrafficArchiverApplication.class, args);
    }
}
