package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.components.marketdata.TickAggregator;
import com.app.shahbaztrades.components.marketdata.WatchlistWarmup;
import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.service.TradeEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy-trading")
public class StrategyTradingController {

    private final TradeEngine tradeEngine;
    private final WatchlistWarmup watchlistWarmup;
    private final TickAggregator tickAggregator;
    private final MarketFeed marketFeed;

    @PublicEndpoint
    @PostMapping("/continuous")
    public ResponseEntity<ApiResponse<Void>> continuousTrade() {
        tradeEngine.continuousTrade();
        return ResponseEntity.ok(ApiResponse.ok(null, "Continuous trading triggered"));
    }

    @PublicEndpoint
    @PostMapping("/warmup")
    public ResponseEntity<ApiResponse<Void>> warmup() {
        watchlistWarmup.warmup();
        return ResponseEntity.ok(ApiResponse.ok(null, "Warmup triggered"));
    }

    @PublicEndpoint
    @PostMapping("/start-container")
    public ResponseEntity<ApiResponse<Void>> startContainer() {
        tickAggregator.startWorkersForActiveWatchlist(marketFeed::subscribe);
        return ResponseEntity.ok(ApiResponse.ok(null, "Start container triggered"));
    }

}
