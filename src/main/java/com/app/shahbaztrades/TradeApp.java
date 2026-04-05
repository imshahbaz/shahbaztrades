package com.app.shahbaztrades;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
      //  SecurityFilterAutoConfiguration.class
})
public class TradeApp {

    public static void main(String[] args) {
        SpringApplication.run(TradeApp.class, args);
    }

}
