package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.model.dto.rupeezy.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "rupeezy-client", url = "https://vortex-api.rupeezy.in/v2")
public interface RupeezyClient {

    @PostMapping("/user/session")
    RupeezySessionResponse generateAccessToken(@RequestBody RupeezySessionRequest request);

    @PostMapping("/trading/orders/regular")
    RupeezyOrderResponseDto placeOrder(@RequestBody RupeezyOrderDto request, @RequestHeader("x-api-key") String apiKey,
                                       @RequestHeader("Authorization") String authorization);

    @PutMapping("/trading/orders/regular/{orderId}")
    RupeezyOrderResponseDto updateOrder(@PathVariable String orderId, @RequestBody RupeezyOrderDto request, @RequestHeader("x-api-key") String apiKey,
                                        @RequestHeader("Authorization") String authorization);

    @DeleteMapping("/trading/orders/regular/{orderId}")
    RupeezyOrderResponseDto cancelOrder(@PathVariable String orderId, @RequestHeader("x-api-key") String apiKey,
                                        @RequestHeader("Authorization") String authorization);

    @GetMapping("/trading/orders")
    RupeezyOrderHistory getOrder(@RequestHeader("x-api-key") String apiKey,
                                 @RequestHeader("Authorization") String authorization);

    @GetMapping("/user/funds")
    Map<String, Object> getUserFunds(@RequestHeader("x-api-key") String apiKey, @RequestHeader("Authorization") String authorization);
}
