package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.strategy.ContinuousTradingStrategy;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StrategyRegistry {

    private final Map<String, List<String>> strategyTokenMap = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, String> tokenSymbolMap = new ConcurrentHashMap<>();
    private final Map<String, ContinuousTradingStrategy> availableStrategies;

    public StrategyRegistry(List<ContinuousTradingStrategy> strategies) {
        this.availableStrategies = strategies.stream()
                .collect(Collectors.toMap(
                        ContinuousTradingStrategy::getName,
                        Function.identity()
                ));
    }

    public void assignTokenToStrategy(String strategyName, String token, String symbol) {
        if (!availableStrategies.containsKey(strategyName)) {
            throw new IllegalArgumentException("Strategy not found: " + strategyName);
        }

        var tokens = strategyTokenMap.computeIfAbsent(strategyName, _ -> new CopyOnWriteArrayList<>());
        if (!tokens.contains(token)) {
            tokens.add(token);
        }

        tokenSymbolMap.put(token, symbol);
    }

    public List<String> getTokensForStrategy(String strategyName) {
        return strategyTokenMap.getOrDefault(strategyName, List.of());
    }

    public List<String> getAllActiveTokens() {
        return strategyTokenMap.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    public ContinuousTradingStrategy getStrategyInstance(String strategyName) {
        return availableStrategies.get(strategyName);
    }

    public void clearRegistry() {
        strategyTokenMap.clear();
        tokenSymbolMap.clear();
    }

}