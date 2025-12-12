package com.bf4invest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Bf4InvestApplication {

    public static void main(String[] args) {
        SpringApplication.run(Bf4InvestApplication.class, args);
    }
}




