package com.example.ephemeral;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EphemeralVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EphemeralVideoApplication.class, args);
    }
}
