package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.service.ChartInkService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chartink")
@RequiredArgsConstructor
public class ChartInkController {

    private final ChartInkService chartInkService;

    @PublicEndpoint
    @GetMapping("/fetchWithMargin")
    public ResponseEntity<ApiResponse<List<StockMarginDto>>> fetchWithMargin(@RequestParam @NotBlank String strategy) {
        var result = chartInkService.fetchWithMargin(strategy);
        return ResponseEntity.ok(ApiResponse.ok(result, "Success"));
    }

}
