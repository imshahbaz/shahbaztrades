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
class GenAiAnalysisServiceImplTest {

    @Mock
    private GenAiClient genAiClient;
    @Mock
    private YahooClient yahooClient;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private GenAiRedisRepo<AIAnalysis> genAiRedisRepo;
    @Mock
    private RLock lock;

    private GenAiAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GenAiAnalysisServiceImpl(genAiClient, yahooClient, mongoConfigService,
                genAiRedisRepo, JsonMapper.builder().build());
    }

    private void stubGeminiKey() {
        var googleAuth = new ServerConfigurations.GoogleAuthCredentials();
        googleAuth.setGeminiKey("gemini-key");
        var config = new ServerConfigurations();
        config.setGoogleAuth(googleAuth);
        when(mongoConfigService.getConfig()).thenReturn(config);
    }

    // --- news -------------------------------------------------------------

    @Test
    void getGenAiAnalysis_servesTheCacheWithoutTakingTheDistributedLock() {
        var cached = new AIAnalysis();
        cached.setAction("BUY");
        when(genAiRedisRepo.get("TCS")).thenReturn(cached);

        assertSame(cached, service.getGenAiAnalysis("TCS"));

        verify(genAiRedisRepo, never()).getLock(anyString());
    }

    @Test
    void getGenAiAnalysis_reChecksTheCacheAfterWinningTheLock() throws Exception {
        // Whoever loses the race must not pay for a second Gemini call.
        var populated = new AIAnalysis();
        populated.setAction("HOLD");
        when(genAiRedisRepo.get("TCS")).thenReturn(null, populated);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(true);

        assertSame(populated, service.getGenAiAnalysis("TCS"));

        verify(genAiClient, never()).getGenAiStockAnalysis(anyString(), anyList(), anyString());
    }

    @Test
    void getGenAiAnalysis_parsesAndCachesAFreshModelResponse() throws Exception {
        when(genAiRedisRepo.get("TCS")).thenReturn(null);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(true);
        when(yahooClient.getMonthlyHistoricalData("TCS")).thenReturn(List.of(
                com.app.shahbaztrades.model.dto.nse.NSEHistoricalData.builder().symbol("TCS").close(3200).build()));
        stubGeminiKey();
        when(genAiClient.getGenAiStockAnalysis(eq("TCS"), anyList(), eq("gemini-key")))
                .thenReturn("{\"action\":\"BUY\",\"confidence\":80,\"trend\":\"Bullish\"}");

        AIAnalysis analysis = service.getGenAiAnalysis("TCS");

        assertEquals("BUY", analysis.getAction());
        assertEquals(80, analysis.getConfidence());
        verify(genAiRedisRepo).set(eq("TCS"), any(AIAnalysis.class), any());
    }

    @Test
    void getGenAiAnalysis_reportsNotFoundWhenThereIsNoPriceHistory() throws Exception {
        when(genAiRedisRepo.get("TCS")).thenReturn(null);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(true);
        when(yahooClient.getMonthlyHistoricalData("TCS")).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> service.getGenAiAnalysis("TCS"));
        verify(genAiClient, never()).getGenAiStockAnalysis(anyString(), anyList(), anyString());
    }

    @Test
    void getGenAiAnalysis_reportsNotFoundWhenTheModelReturnsNothing() throws Exception {
        when(genAiRedisRepo.get("TCS")).thenReturn(null);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(true);
        when(yahooClient.getMonthlyHistoricalData("TCS")).thenReturn(List.of(
                com.app.shahbaztrades.model.dto.nse.NSEHistoricalData.builder().symbol("TCS").close(3200).build()));
        stubGeminiKey();
        when(genAiClient.getGenAiStockAnalysis(anyString(), anyList(), anyString())).thenReturn("");

        assertThrows(NotFoundException.class, () -> service.getGenAiAnalysis("TCS"));
    }

    @Test
    void getGenAiAnalysis_reportsNotFoundWhenTheLockCannotBeAcquired() throws Exception {
        when(genAiRedisRepo.get("TCS")).thenReturn(null);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service.getGenAiAnalysis("TCS"));
    }

    @Test
    void getGenAiAnalysis_reportsNotFoundWhenTheModelReturnsUnparseableJson() throws Exception {
        when(genAiRedisRepo.get("TCS")).thenReturn(null);
        when(genAiRedisRepo.getLock("TCS")).thenReturn(lock);
        when(lock.tryLock(20, -1, TimeUnit.SECONDS)).thenReturn(true);
        when(yahooClient.getMonthlyHistoricalData("TCS")).thenReturn(List.of(
                com.app.shahbaztrades.model.dto.nse.NSEHistoricalData.builder().symbol("TCS").close(3200).build()));
        stubGeminiKey();
        when(genAiClient.getGenAiStockAnalysis(anyString(), anyList(), anyString())).thenReturn("not json");

        assertThrows(NotFoundException.class, () -> service.getGenAiAnalysis("TCS"));
    }
}
