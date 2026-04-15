package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface StrategyService {

    Map<String, StrategyDto> getCachedStrategies();

    void refreshStrategyCache();

    ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies();

    ResponseEntity<ApiResponse<StrategyDto>> createStrategy(StrategyDto strategyDto);

    ResponseEntity<ApiResponse<StrategyDto>> updateStrategy(StrategyDto strategyDto);

    ResponseEntity<ApiResponse<Void>> deleteStrategy(String id);

    ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategiesAdmin();
}
