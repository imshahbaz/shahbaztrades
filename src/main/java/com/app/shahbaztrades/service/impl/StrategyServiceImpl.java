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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategyServiceImpl implements StrategyService {

    private List<StrategyDto> cachedStrategies = new ArrayList<>();
    private final StrategyRepository strategyRepository;

    @PostConstruct
    public void init() {
        refreshStrategyCache();
    }

    public void refreshStrategyCache() {
        var strategies = strategyRepository.findAll();
        if (!CollectionUtils.isEmpty(strategies)) {
            cachedStrategies = strategies.stream().map(Strategy::toDto).toList();
        }
    }

    @Override
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategies() {
        return ResponseEntity.ok(ApiResponse.ok(getActiveStrategies(Boolean.TRUE), "Strategies fetched successfully"));
    }

    private List<StrategyDto> getActiveStrategies(boolean active) {
        if (active) {
            return cachedStrategies.stream().filter(StrategyDto::isActive).toList();
        } else {
            return cachedStrategies;
        }
    }

}
