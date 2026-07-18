package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.service.AnalysisService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news")
@Validated
public class NewsController {

    private final AnalysisService analysisService;

    @PublicEndpoint
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(@PathVariable @NotBlank String symbol) {
        return analysisService.getStockNews(symbol);
    }

    @PublicEndpoint
    @GetMapping("/ai/{symbol}")
    public ResponseEntity<ApiResponse<AIAnalysis>> getAiAnalysis(@PathVariable @NotBlank String symbol) {
        return analysisService.getGenAiAnalysis(symbol);
    }

    @PublicEndpoint
    @PostMapping("/update-strategy-backtest")
    public ResponseEntity<ApiResponse<Boolean>> updateStrategyBacktestData() {
        analysisService.updateStrategyBacktestData();
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE, "Backtest data update started"));
    }

}
