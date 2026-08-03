package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.strategy.DailyTradingStrategy;
import com.app.shahbaztrades.exceptions.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyTradingStrategyRegistry {

    private final Map<String, DailyTradingStrategy> availableStrategies;

    public DailyTradingStrategyRegistry(List<DailyTradingStrategy> strategies) {
        this.availableStrategies = strategies.stream()
                .collect(Collectors.toMap(
                        DailyTradingStrategy::getName,
                        Function.identity()
                ));
    }

    public DailyTradingStrategy getStrategy(String type) {
        return Optional.ofNullable(availableStrategies.get(type))
                .orElseThrow(() -> new NotFoundException("Strategy not supported for " + type));
    }

}
