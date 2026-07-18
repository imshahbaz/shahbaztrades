package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.repo.StrategyRepository;
import com.app.shahbaztrades.service.StrategyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StrategyServiceImpl implements StrategyService {

    private final StrategyRepository strategyRepository;
    private Map<String, StrategyDto> cachedStrategies = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshStrategyCache();
    }

    @Override
    public Map<String, StrategyDto> getCachedStrategies() {
        return cachedStrategies;
    }

    @Override
    public void refreshStrategyCache() {
        var strategies = strategyRepository.findAll();
        // Always rebuild the map (even when empty) so deletions are reflected, and key by a
        // canonical upper-case name so writes and look-ups never diverge on casing.
        cachedStrategies = strategies.stream()
                .collect(Collectors.toConcurrentMap(
                        s -> s.getName().toUpperCase(),
                        Strategy::toDto
                ));
    }

    @Override
    public List<StrategyDto> getAllStrategies() {
        return getActiveStrategies(Boolean.TRUE);
    }

    @Override
    public StrategyDto createStrategy(StrategyDto strategyDto) {
        try {
            strategyRepository.insert(strategyDto.toEntity());
            cachedStrategies.put(strategyDto.getName().toUpperCase(), strategyDto);
        } catch (Exception _) {
            throw new ResourceAlreadyExistsException("Strategy with name " + strategyDto.getName() + " already exists");
        }
        return strategyDto;
    }

    @Override
    public StrategyDto updateStrategy(StrategyDto strategyDto) {
        strategyRepository.save(strategyDto.toEntity());
        cachedStrategies.put(strategyDto.getName().toUpperCase(), strategyDto);
        return strategyDto;
    }

    @Override
    public void deleteStrategy(String id) {
        strategyRepository.deleteById(id);
        // The cache is keyed by strategy name, not id, so rebuild it from source to evict correctly.
        refreshStrategyCache();
    }

    @Override
    public List<StrategyDto> getAllStrategiesAdmin() {
        return getActiveStrategies(Boolean.FALSE);
    }

    private List<StrategyDto> getActiveStrategies(boolean active) {
        if (active) {
            return cachedStrategies.values().stream().filter(StrategyDto::isActive).toList();
        } else {
            return cachedStrategies.values().stream().toList();
        }
    }

}
