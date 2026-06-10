package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface NseService {

    ResponseEntity<ApiResponse<List<NSEHistoricalData>>> getHistoricalData(String symbol);
}
