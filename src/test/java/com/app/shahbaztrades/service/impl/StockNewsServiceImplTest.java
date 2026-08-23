package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.components.analysis.GenAiClient;
import com.app.shahbaztrades.components.analysis.TradingViewClient;
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
class StockNewsServiceImplTest {

    @Mock
    private TradingViewClient tradingViewClient;
    @Mock
    private TvNewsRedisRepo<List<TradingViewNewsResponse.NewsItem>> tvNewsRedisRepo;

    private StockNewsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StockNewsServiceImpl(tradingViewClient, tvNewsRedisRepo);
    }

    @Test
    void getStockNews_servesTheCacheWithoutCallingTradingView() {
        var cached = List.of(new TradingViewNewsResponse.NewsItem("Headline", 1L));
        when(tvNewsRedisRepo.get("TCS")).thenReturn(cached);

        assertSame(cached, service.getStockNews("TCS"));

        verify(tvNewsRedisRepo, never()).set(anyString(), anyList(), any());
    }
}
