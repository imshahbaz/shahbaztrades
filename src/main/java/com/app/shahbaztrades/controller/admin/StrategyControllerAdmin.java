package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.service.StrategyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/strategy")
@RequiredArgsConstructor
public class StrategyControllerAdmin {

    private final StrategyService strategyService;

    @AdminOnly
    @PostMapping
    public ResponseEntity<ApiResponse<StrategyDto>> createStrategy(@RequestBody @Valid StrategyDto strategyDto) {
        return ResponseEntity.ok(ApiResponse.ok(strategyService.createStrategy(strategyDto), "Strategy created successfully"));
    }

    @AdminOnly
    @PutMapping
    public ResponseEntity<ApiResponse<StrategyDto>> updateStrategy(@RequestBody @Valid StrategyDto strategyDto) {
        return ResponseEntity.ok(ApiResponse.ok(strategyService.updateStrategy(strategyDto), "Strategy updated successfully"));
    }

    @AdminOnly
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteStrategy(@RequestParam @NotBlank String id) {
        strategyService.deleteStrategy(id.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(null, "Strategy deleted successfully"));
    }

    @AdminOnly
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategiesAdmin() {
        return ResponseEntity.ok(ApiResponse.ok(strategyService.getAllStrategiesAdmin(), "Strategies fetched successfully"));
    }

}
