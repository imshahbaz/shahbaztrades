package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkResponseDto;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.service.ChartInkService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/chartink")
@RequiredArgsConstructor
public class ChartInkController {

    private static final String SUCCESS_MSG = "Success";

    private final ChartInkService chartInkService;

    @PublicEndpoint
    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<ChartInkResponseDto>> fetch(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchData(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, SUCCESS_MSG));
    }

    @PublicEndpoint
    @GetMapping("/fetchWithMargin")
    public ResponseEntity<ApiResponse<List<StockMarginDto>>> fetchWithMargin(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchWithMargin(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, SUCCESS_MSG));
    }

    @PublicEndpoint
    @GetMapping("/backtest")
    public ResponseEntity<ApiResponse<List<ChartInkBacktestDto>>> fetchBackTestData(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchBacktestData(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, SUCCESS_MSG));
    }

    @PublicEndpoint
    @GetMapping("/backtestWithMargin")
    public ResponseEntity<ApiResponse<List<ChartInkBacktestMarginDto>>> fetchBackTestDataWithMargin(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchBacktestDataWithMargin(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, SUCCESS_MSG));
    }

    @PublicEndpoint
    @GetMapping("/backtestTodayWithMargin")
    public ResponseEntity<ApiResponse<List<ChartInkBacktestMarginDto>>> fetchBackTodayTestDataWithMargin(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchTodayBacktestDataWithMargin(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, SUCCESS_MSG));
    }

}
