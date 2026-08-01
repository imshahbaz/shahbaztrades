package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.enums.TimeFrame;

import java.util.List;
import java.util.Map;

public interface StrategyService {

    Map<String, StrategyDto> getCachedStrategies();

    void refreshStrategyCache();

    List<StrategyDto> getAllStrategies(TimeFrame timeFrame);

    StrategyDto createStrategy(StrategyDto strategyDto);

    StrategyDto updateStrategy(StrategyDto strategyDto);

    void deleteStrategy(String id);

    List<StrategyDto> getAllStrategiesAdmin();
}
