package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
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

    @Override
    public ResponseEntity<ApiResponse<StrategyDto>> createStrategy(StrategyDto strategyDto) {
        try {
            strategyRepository.insert(strategyDto.toEntity());
            cachedStrategies.put(strategyDto.getName().toUpperCase(), strategyDto);
        } catch (Exception e) {
            throw new ResourceAlreadyExistsException("Strategy with name " + strategyDto.getName() + " already exists");
        }
        return ResponseEntity.ok(ApiResponse.ok(strategyDto, "Strategy created successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<StrategyDto>> updateStrategy(StrategyDto strategyDto) {
        strategyRepository.save(strategyDto.toEntity());
        cachedStrategies.put(strategyDto.getName().toUpperCase(), strategyDto);
        return ResponseEntity.ok(ApiResponse.ok(strategyDto, "Strategy updated successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> deleteStrategy(String id) {
        strategyRepository.deleteById(id);
        cachedStrategies.remove(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Strategy deleted successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<List<StrategyDto>>> getAllStrategiesAdmin() {
        return ResponseEntity.ok(ApiResponse.ok(getActiveStrategies(Boolean.FALSE), "Strategies fetched successfully"));
    }

    private List<StrategyDto> getActiveStrategies(boolean active) {
        if (active) {
            return cachedStrategies.values().stream().filter(StrategyDto::isActive).toList();
        } else {
            return cachedStrategies.values().stream().toList();
        }
    }

}
