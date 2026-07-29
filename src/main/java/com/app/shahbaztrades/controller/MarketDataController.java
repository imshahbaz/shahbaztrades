package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketDataController {

    private final StrategyRegistry strategyRegistry;
    private final MarginService marginService;
    private final MarketDataContainer marketDataContainer;

    @PublicEndpoint
    @GetMapping("/bar-series/{symbol}")
    private ResponseEntity<ApiResponse<List<SmartApiLtpResponse.CandleDetail>>> getBarSeries(@PathVariable @NotBlank String symbol) {
        var margin = marginService.getMargin(symbol);
        if (strategyRegistry.getTokenSymbolMap().get(margin.getToken()) == null) {
            throw new NotFoundException("Bar Series Not Found");
        }

        var response = marketDataContainer.snapshotSeries(margin.getToken()).getBarData().stream()
                .map(bar -> SmartApiLtpResponse.CandleDetail.builder().timestamp(bar.getSystemZonedBeginTime())
                        .open(bar.getOpenPrice().doubleValue())
                        .high(bar.getHighPrice().doubleValue())
                        .low(bar.getLowPrice().doubleValue())
                        .close(bar.getClosePrice().doubleValue())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(response, "Bar Series Fetched"));
    }
}
