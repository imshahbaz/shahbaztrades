package com.shahbaz.trades;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TradesApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradesApplication.class, args);
    }

}
