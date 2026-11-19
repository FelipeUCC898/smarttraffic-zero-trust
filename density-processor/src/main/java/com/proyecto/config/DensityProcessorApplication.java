package com.proyecto.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.proyecto")
public class DensityProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(DensityProcessorApplication.class, args);
    }
}
