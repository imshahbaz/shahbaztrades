package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.service.StrategyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    @GetMapping
    @PublicEndpoint
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies() {
        return strategyService.getAllStrategies();
    }

    @AdminOnly
    @PostMapping
    public ResponseEntity<ApiResponse<StrategyDto>> createStrategy(@RequestBody @Valid StrategyDto strategyDto) {
        return strategyService.createStrategy(strategyDto);
    }

    @AdminOnly
    @PutMapping
    public ResponseEntity<ApiResponse<StrategyDto>> updateStrategy(@RequestBody @Valid StrategyDto strategyDto) {
        return strategyService.updateStrategy(strategyDto);
    }

    @AdminOnly
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteStrategy(@RequestParam @NotBlank String id) {
        return strategyService.deleteStrategy(id.toUpperCase());
    }

    @AdminOnly
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategiesAdmin() {
        return strategyService.getAllStrategiesAdmin();
    }

}
