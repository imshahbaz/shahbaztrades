package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.service.StrategyTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy-trading")
public class StrategyTradingController {

    private final StrategyTradingService strategyTradingService;

    @PostMapping("/continuous")
    public ResponseEntity<ApiResponse<Void>> continuousTrade() {
        strategyTradingService.continuousTrade();
        return ResponseEntity.ok(ApiResponse.ok(null, "Continuous trading triggered"));
    }

}
