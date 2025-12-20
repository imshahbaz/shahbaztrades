package com.shahbaz.trades.controller;

import com.shahbaz.trades.model.dto.StockMarginDto;
import com.shahbaz.trades.model.dto.response.ChartInkResponseDto;
import com.shahbaz.trades.service.ChartInkService;
import com.shahbaz.trades.service.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chartink")
public class ChartInkController {

    private final ChartInkService chartinkService;

    @GetMapping("/fetch")
    public ResponseEntity<ChartInkResponseDto> fetchData(String strategy) {
        return ResponseEntity.ok(chartinkService.fetchData(StrategyService.strategyMap.get(strategy)));
    }

    @GetMapping("/fetchWithMargin")
    public ResponseEntity<List<StockMarginDto>> fetchWithMargin(String strategy) {
        return ResponseEntity.ok(chartinkService.fetchWithMargin(StrategyService.strategyMap.get(strategy)));
    }

}
