package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.model.dto.StrategyDto;
import com.shahbaz.trades.model.entity.Strategy;
import com.shahbaz.trades.repository.StrategyRepository;
import com.shahbaz.trades.service.StrategyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StrategyServiceImpl implements StrategyService {

    private final StrategyRepository strategyRepository;

    @Override
    @PostConstruct
    public void reloadAllStrategies() {
        strategyRepository.findAll().stream()
                .filter(Strategy::isActive)
                .map(Strategy::toDto)
                .forEach(dto -> strategyMap.put(dto.getName(), dto));
    }

    @Override
    public List<StrategyDto> getAllStrategy() {
        return List.copyOf(strategyMap.values());
    }

    @Override
    public StrategyDto createStrategy(StrategyDto request) {
        return strategyRepository.save(request.toEntity()).toDto();
    }

    @Override
    public StrategyDto updateStrategy(StrategyDto request) {
        return strategyRepository.save(request.toEntity()).toDto();
    }

    @Override
    public void deleteStrategy(String id) {
        strategyRepository.deleteById(id);
    }

}
