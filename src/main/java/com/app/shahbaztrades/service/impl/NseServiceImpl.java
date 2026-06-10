package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.service.NseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NseServiceImpl implements NseService {

    private final YahooClient yahooClient;

    @Override
    public ResponseEntity<ApiResponse<List<NSEHistoricalData>>> getHistoricalData(String symbol) {
        return ResponseEntity.ok(ApiResponse.ok(yahooClient.getHistoricalData(symbol, YahooTimeRange.RANGE_1MO.getValue()), "Historical Data Fetched"));
    }
}
