package com.app.shahbaztrades.components.strategy;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
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

    /**
     * Screener key to the strategies fed by it, in a stable order. Drives warmup, so a new strategy
     * bean joins the rotation without the warmup code knowing it exists.
     */
    public Map<String, List<String>> strategyNamesByWatchlistKey() {
        return availableStrategies.values().stream()
                .collect(Collectors.groupingBy(
                        ContinuousTradingStrategy::watchlistKey,
                        TreeMap::new,
                        Collectors.mapping(ContinuousTradingStrategy::getName, Collectors.toList())
                ));
    }

    public void clearRegistry() {
        strategyTokenMap.clear();
        tokenSymbolMap.clear();
    }

}