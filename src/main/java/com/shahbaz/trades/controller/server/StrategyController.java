package com.shahbaz.trades.controller.server;

import com.shahbaz.trades.model.dto.StrategyDto;
import com.shahbaz.trades.service.StrategyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    @GetMapping
    public List<StrategyDto> getAllStrategies() {
        return strategyService.getAllStrategy();
    }

    @PostMapping
    public StrategyDto createStrategy(@Valid @RequestBody StrategyDto request) {
        return strategyService.createStrategy(request);
    }

    @PutMapping
    public StrategyDto updateStrategy(@Valid @RequestBody StrategyDto request) {
        return strategyService.updateStrategy(request);
    }

    @DeleteMapping
    public void deleteStrategy(@NotBlank @PathVariable String id) {
        strategyService.deleteStrategy(id);
    }

    @PostMapping("/reload")
    public void reloadAllStrategies() {
        strategyService.reloadAllStrategies();
    }

}
