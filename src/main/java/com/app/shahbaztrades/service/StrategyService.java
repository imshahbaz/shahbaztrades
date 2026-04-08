package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface StrategyService {

    Map<String,StrategyDto> getCachedStrategies();

    void refreshStrategyCache();

    ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies();
}
