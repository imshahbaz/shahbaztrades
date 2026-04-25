package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrderById(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getById(id), "Order fetched successfully"));
    }

    @GetMapping("/date")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getOrdersByDate(@RequestParam @NotBlank String date) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersByDate(date), "Orders fetched successfully"));
    }

    @GetMapping("user/{userId}")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getOrdersByUserId(@PathVariable @Min(1) long userId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrdersByUserId(userId), "Orders fetched successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createOrder(@RequestBody @Valid OrderDto orderDto) {
        orderService.createOrder(orderDto);
        return new ResponseEntity<>(ApiResponse.ok(null, "Order created successfully"), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateOrder(@RequestBody @Valid OrderDto orderDto,
                                                         @PathVariable @NotBlank String id) {
        orderDto.setId(id);
        orderService.updateOrder(orderDto);
        return new ResponseEntity<>(ApiResponse.ok(null, "Order updated successfully"), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable @NotBlank String id) {
        orderService.deleteOrder(id);
        return new ResponseEntity<>(ApiResponse.ok(null, "Order deleted successfully"), HttpStatus.OK);
    }

    @PostMapping("/initiate-mtf")
    public ResponseEntity<ApiResponse<Void>> initiateMtfOrders() {
        orderService.initiateMtfOrders();
        return new ResponseEntity<>(ApiResponse.ok(null, "Orders initiated successfully"), HttpStatus.OK);
    }

    @PostMapping("/update-status")
    public ResponseEntity<ApiResponse<Void>> updateOrderStatus() {
        orderService.updateMtfOrderStatus();
        return ResponseEntity.ok(ApiResponse.ok(null, "Order status update triggered"));
    }

    @PostMapping("/start-trading")
    public ResponseEntity<ApiResponse<Void>> startTrading() {
        orderService.startTrading();
        return ResponseEntity.ok(ApiResponse.ok(null, "Trading started successfully"));
    }

}
