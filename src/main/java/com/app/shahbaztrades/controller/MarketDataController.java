package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.components.marketdata.BarSeriesStore;
import com.app.shahbaztrades.components.strategy.StrategyRegistry;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.market.Candle;
import com.app.shahbaztrades.service.MarginService;
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
    private final BarSeriesStore barSeriesStore;

    @PublicEndpoint
    @GetMapping("/bar-series/{symbol}")
    private ResponseEntity<ApiResponse<List<Candle>>> getBarSeries(@PathVariable @NotBlank String symbol) {
        var margin = marginService.getMargin(symbol);
        if (strategyRegistry.getTokenSymbolMap().get(margin.getToken()) == null) {
            throw new NotFoundException("Bar Series Not Found");
        }

        var response = barSeriesStore.snapshot(margin.getToken()).getBarData().stream()
                .map(bar -> Candle.builder().timestamp(bar.getSystemZonedBeginTime())
                        .open(bar.getOpenPrice().doubleValue())
                        .high(bar.getHighPrice().doubleValue())
                        .low(bar.getLowPrice().doubleValue())
                        .close(bar.getClosePrice().doubleValue())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(response, "Bar Series Fetched"));
    }
}
