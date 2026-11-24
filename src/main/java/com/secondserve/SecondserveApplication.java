package com.secondserve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecondserveApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecondserveApplication.class, args);
    }
}