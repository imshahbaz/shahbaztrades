package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.strategy.StrategyDto;

import java.util.List;
import java.util.Map;

public interface StrategyService {

    Map<String, StrategyDto> getCachedStrategies();

    void refreshStrategyCache();

    List<StrategyDto> getAllStrategies();

    StrategyDto createStrategy(StrategyDto strategyDto);

    StrategyDto updateStrategy(StrategyDto strategyDto);

    void deleteStrategy(String id);

    List<StrategyDto> getAllStrategiesAdmin();
}
