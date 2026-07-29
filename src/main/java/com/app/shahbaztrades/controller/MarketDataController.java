package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/market")
public class MarketDataController {

    private final StrategyRegistry strategyRegistry;
    private final MarginService marginService;
    private final MarketDataContainer marketDataContainer;

    @PublicEndpoint
    @GetMapping("/bar-series/{symbol}")
    private ResponseEntity<ApiResponse<BarSeries>> getBarSeries(@PathVariable @NotBlank String symbol) {
        var margin = marginService.getMargin(symbol);
        var token = strategyRegistry.getTokenSymbolMap().get(margin.getToken());
        if (StringUtils.isEmpty(token)) {
            throw new NotFoundException("Bar Series Not Found");
        }
        return ResponseEntity.ok(ApiResponse.ok(marketDataContainer.snapshotSeries(token), "Bar Series Fetched"));
    }
}
