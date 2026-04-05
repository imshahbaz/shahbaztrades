package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StrategyService {

    ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies();
}
