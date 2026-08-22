package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.service.GenAiAnalysisService;
import com.app.shahbaztrades.service.StockNewsService;
import com.app.shahbaztrades.service.StrategyBacktestService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news")
public class NewsController {

    private static final String NEWS_FETCHED_SUCCESS_MSG = "News Fetched Successfully";

    private final StockNewsService stockNewsService;
    private final GenAiAnalysisService genAiAnalysisService;
    private final StrategyBacktestService strategyBacktestService;

    @PublicEndpoint
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(@PathVariable @NotBlank String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(stockNewsService.getStockNews(symbol), NEWS_FETCHED_SUCCESS_MSG));
    }

    @PublicEndpoint
    @GetMapping("/ai/{symbol}")
    public ResponseEntity<ApiResponse<AIAnalysis>> getAiAnalysis(@PathVariable @NotBlank String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(genAiAnalysisService.getGenAiAnalysis(symbol), "Ai Analysis Fetched Successfully"));
    }

    @PublicEndpoint
    @PostMapping("/update-strategy-backtest")
    public ResponseEntity<ApiResponse<Boolean>> updateStrategyBacktestData() {
        strategyBacktestService.updateStrategyBacktestData();
        return ResponseEntity.ok(ApiResponse.ok(Boolean.TRUE, "Backtest data update started"));
    }

}
