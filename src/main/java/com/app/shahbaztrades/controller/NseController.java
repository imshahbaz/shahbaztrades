package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.service.NseService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nse")
@Validated
public class NseController {

    private final NseService nseService;

    @PublicEndpoint
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<NSEHistoricalData>>> getHistoricalData(@RequestParam @NotBlank String symbol) {
        return nseService.getHistoricalData(symbol);
    }

}
