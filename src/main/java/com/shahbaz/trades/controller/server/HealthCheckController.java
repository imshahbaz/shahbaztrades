package com.shahbaz.trades.controller.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    @RequestMapping(value = "/health", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok().build();
    }
}