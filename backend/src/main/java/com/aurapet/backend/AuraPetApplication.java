package com.aurapet.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuraPetApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuraPetApplication.class, args);
    }
}
