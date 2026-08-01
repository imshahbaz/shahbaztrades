package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.service.StrategyOrderService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/strategy-order")
public class StrategyOrderControllerAdmin {

    private final StrategyOrderService strategyOrderService;

    @AdminOnly
    @GetMapping
    public ResponseEntity<ApiResponse<List<StrategyOrderDto>>> getAllOrdersAdmin(@RequestParam @NotBlank String strategyName) {
        return ResponseEntity.ok(ApiResponse.ok(strategyOrderService.getAllOrdersAdmin(strategyName), "Strategy orders fetched successfully"));
    }

    @AdminOnly
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StrategyOrderDto>> getOrderById(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(strategyOrderService.getOrderById(id), "Strategy order fetched successfully"));
    }

}
