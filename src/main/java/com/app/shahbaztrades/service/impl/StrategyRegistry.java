package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.strategy.TradingStrategy;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class StrategyRegistry {

    private final Map<String, List<String>> strategyTokenMap = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, String> tokenSymbolMap = new ConcurrentHashMap<>();
    private final Map<String, TradingStrategy> availableStrategies;

    public StrategyRegistry(Map<String, TradingStrategy> strategyBeans) {
        this.availableStrategies = strategyBeans;
    }

    public void assignTokenToStrategy(String strategyName, String token, String symbol) {
        if (!availableStrategies.containsKey(strategyName)) {
            throw new IllegalArgumentException("Strategy not found: " + strategyName);
        }

        strategyTokenMap.computeIfAbsent(strategyName, k -> new CopyOnWriteArrayList<>())
                .add(token);

        tokenSymbolMap.put(token, symbol);
    }

    public List<String> getTokensForStrategy(String strategyName) {
        return strategyTokenMap.getOrDefault(strategyName, List.of());
    }

    public List<String> getAllActiveTokens() {
        return strategyTokenMap.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    public TradingStrategy getStrategyInstance(String strategyName) {
        return availableStrategies.get(strategyName);
    }

    public void clearRegistry() {
        strategyTokenMap.clear();
        tokenSymbolMap.clear();
    }

}