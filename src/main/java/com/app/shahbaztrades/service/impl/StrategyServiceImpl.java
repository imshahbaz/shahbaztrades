package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.repo.StrategyRepository;
import com.app.shahbaztrades.service.StrategyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StrategyServiceImpl implements StrategyService {

    private Map<String, StrategyDto> cachedStrategies = new HashMap<>();
    private final StrategyRepository strategyRepository;

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
        if (!CollectionUtils.isEmpty(strategies)) {
            cachedStrategies = strategies.stream()
                    .collect(Collectors.toMap(
                            Strategy::getName,
                            Strategy::toDto
                    ));
        }
    }

    @Override
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies() {
        return ResponseEntity.ok(ApiResponse.ok(getActiveStrategies(Boolean.TRUE), "Strategies fetched successfully"));
    }

    private List<StrategyDto> getActiveStrategies(boolean active) {
        if (active) {
            return cachedStrategies.values().stream().filter(StrategyDto::isActive).toList();
        } else {
            return cachedStrategies.values().stream().toList();
        }
    }

}
