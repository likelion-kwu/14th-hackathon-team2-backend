package com.likelion.hackathon_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HackathonBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HackathonBeApplication.class, args);
    }

}
