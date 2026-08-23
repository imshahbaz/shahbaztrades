package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.components.analysis.GenAiClient;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.repo.redis.GenAiRedisRepo;
import com.app.shahbaztrades.repo.redis.TvNewsRedisRepo;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.StrategyService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyBacktestServiceImplTest {

    @Mock
    private StrategyService strategyService;
    @Mock
    private ChartInkService chartInkService;
    @Mock
    private MarketDataQuery marketDataQuery;
    @Mock
    private MongoTemplate mongoTemplate;

    private StrategyBacktestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyBacktestServiceImpl(strategyService, chartInkService, marketDataQuery, mongoTemplate);
    }

    @Test
    void updateStrategyBacktestData_isANoOpWithoutActiveDailyStrategies() {
        when(strategyService.getAllStrategies(TimeFrame.DAILY)).thenReturn(List.of());

        service.updateStrategyBacktestData();

        verify(chartInkService, never()).fetchBacktestDataWithMargin(anyString());
        verify(strategyService, never()).refreshStrategyCache();
    }

    @Test
    void updateStrategyBacktestData_skipsTheWriteWhenNoTradesCouldBeEvaluated() {
        // Writing a 0% success rate off zero trades would corrupt the strategy ranking.
        when(strategyService.getAllStrategies(TimeFrame.DAILY))
                .thenReturn(List.of(StrategyDto.builder().name("RSI").build()));
        when(chartInkService.fetchBacktestDataWithMargin("RSI")).thenReturn(List.of());

        service.updateStrategyBacktestData();

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Strategy.class));
        verify(strategyService).refreshStrategyCache();
    }

    @Test
    void updateStrategyBacktestData_refreshesTheCacheAfterProcessing() {
        when(strategyService.getAllStrategies(TimeFrame.DAILY))
                .thenReturn(List.of(StrategyDto.builder().name("RSI").build()));
        when(chartInkService.fetchBacktestDataWithMargin("RSI"))
                .thenReturn(List.<ChartInkBacktestMarginDto>of());

        service.updateStrategyBacktestData();

        verify(strategyService).refreshStrategyCache();
    }
}
