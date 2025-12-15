package com.shahbaz.trades.service;

import com.shahbaz.trades.model.dto.StrategyDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface StrategyService {

    Map<String, StrategyDto> strategyMap = new ConcurrentHashMap<>();

    void reloadAllStrategies();

    List<StrategyDto> getAllStrategy();

    StrategyDto createStrategy(StrategyDto request);

    StrategyDto updateStrategy(StrategyDto request);

    void deleteStrategy(String id);
}
