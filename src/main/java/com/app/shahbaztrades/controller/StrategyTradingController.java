package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.service.TradeEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy-trading")
public class StrategyTradingController {

    private final TradeEngine tradeEngine;

    @PublicEndpoint
    @PostMapping("/continuous")
    public ResponseEntity<ApiResponse<Void>> continuousTrade() {
        tradeEngine.continuousTrade();
        return ResponseEntity.ok(ApiResponse.ok(null, "Continuous trading triggered"));
    }

}
