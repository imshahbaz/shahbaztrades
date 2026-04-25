package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.service.StrategyOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy-order")
public class StrategyOrderController {

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

    @PostMapping
    public ResponseEntity<ApiResponse<StrategyOrderDto>> createOrder(@RequestBody @Valid StrategyOrderDto request,
                                                                     @RequestAttribute("user") UserDto userDto) {
        request.setUserId(userDto.getUserId());
        return new ResponseEntity<>(ApiResponse.ok(strategyOrderService.createOrder(request), "Strategy order created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StrategyOrderDto>> updateOrder(@RequestBody @Valid StrategyOrderDto request,
                                                                     @RequestAttribute("user") UserDto userDto,
                                                                     @PathVariable @NotBlank String id) {
        request.setId(id);
        request.setUserId(userDto.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(strategyOrderService.updateOrder(request), "Strategy order updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable @NotBlank String id) {
        strategyOrderService.deleteOrder(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Strategy order deleted successfully"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<StrategyOrderDto>>> getMyOrders(@RequestAttribute("user") UserDto userDto) {
        return ResponseEntity.ok(ApiResponse.ok(strategyOrderService.getOrdersByUserId(userDto.getUserId()), "User strategy orders fetched successfully"));
    }

}
