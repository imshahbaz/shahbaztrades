package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.service.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    @GetMapping
    @PublicEndpoint
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies(@RequestParam(defaultValue = "DAILY", required = false) TimeFrame timeFrame) {
        return ResponseEntity.ok(ApiResponse.ok(strategyService.getAllStrategies(timeFrame), "Strategies fetched successfully"));
    }

}
