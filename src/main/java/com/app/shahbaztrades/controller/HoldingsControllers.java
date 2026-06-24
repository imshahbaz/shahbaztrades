package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.HoldingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/holdings")
public class HoldingsControllers {

    private final HoldingsService holdingsService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<HoldingDto>>> getAllHoldings(@RequestParam @NotNull BrokerType brokerType, @RequestAttribute("user") UserDto userDto) {
        return holdingsService.getAllHoldings(brokerType, userDto);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Boolean>> createHoldings(@RequestParam @NotNull BrokerType brokerType, @RequestAttribute("user") UserDto userDto, @RequestBody @Valid HoldingDto holdingDto) {
        return holdingsService.createHoldings(brokerType, userDto, holdingDto);
    }

    @DeleteMapping("/{symbol}")
    public ResponseEntity<ApiResponse<Boolean>> deleteHoldings(@RequestParam @NotNull BrokerType brokerType, @RequestAttribute("user") UserDto userDto, @PathVariable @NotBlank String symbol) {
        return holdingsService.deleteHoldings(brokerType, userDto, symbol);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Boolean>> updateHoldings(@RequestParam @NotNull BrokerType brokerType, @RequestAttribute("user") UserDto userDto, @RequestBody @Valid HoldingDto holdingDto) {
        return holdingsService.updateHoldings(brokerType, userDto, holdingDto);
    }

    @DeleteMapping("detail/{symbol}/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteHoldingDetail(@RequestParam @NotNull BrokerType brokerType, @RequestAttribute("user") UserDto userDto,
                                                                    @PathVariable @NotBlank String symbol, @PathVariable @Min(1) int id) {
        return holdingsService.deleteHoldingDetail(brokerType, userDto, symbol,id);
    }
}
