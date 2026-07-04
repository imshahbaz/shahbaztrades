package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.analysis.GenAiClient;
import com.app.shahbaztrades.components.analysis.TradingViewClient;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.service.*;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final String NEWS_FETCHED_SUCCESS_MSG = "News Fetched Successfully";

    private final StringRedisTemplate stringRedisTemplate;
    private final GenAiClient genAiClient;
    private final YahooClient yahooClient;
    private final MongoConfigService mongoConfigService;
    private final RedissonClient redissonClient;
    private final StrategyService strategyService;
    private final ChartInkService chartInkService;
    private final AngelOneService angelOneService;
    private final MongoTemplate mongoTemplate;

    @Override
    public ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(String symbol) {
        var cacheKey = "tv_news:" + symbol;
        var value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(value)) {
            List<TradingViewNewsResponse.NewsItem> res = HelperUtil.GSON.fromJson(value, new TypeToken<List<TradingViewNewsResponse.NewsItem>>() {
            }.getType());
            return ResponseEntity.ok(ApiResponse.ok(res, NEWS_FETCHED_SUCCESS_MSG));
        }

        var res = TradingViewClient.getStockNews(symbol);
        if (res != null && !CollectionUtils.isEmpty(res.items())) {
            stringRedisTemplate.opsForValue().set(cacheKey, HelperUtil.GSON.toJson(res.items()), Duration.ofMinutes(10));
            return ResponseEntity.ok(ApiResponse.ok(res.items(), NEWS_FETCHED_SUCCESS_MSG));
        }

        throw new NotFoundException("News Not Found");
    }

    @Override
    public ResponseEntity<ApiResponse<AIAnalysis>> getGenAiAnalysis(String symbol) {
        var cacheKey = "genai:" + symbol;

        var value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(value)) {
            var res = HelperUtil.GSON.fromJson(value, AIAnalysis.class);
            return ResponseEntity.ok(ApiResponse.ok(res, NEWS_FETCHED_SUCCESS_MSG));
        }

        var lockKey = "lock:genai_" + symbol;
        var lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(20, -1, TimeUnit.SECONDS)) {
                try {
                    value = stringRedisTemplate.opsForValue().get(cacheKey);
                    if (StringUtils.isNotBlank(value)) {
                        var res = HelperUtil.GSON.fromJson(value, AIAnalysis.class);
                        return ResponseEntity.ok(ApiResponse.ok(res, NEWS_FETCHED_SUCCESS_MSG));
                    }

                    var history = yahooClient.getHistoricalData(symbol, YahooTimeRange.RANGE_1MO.getValue());
                    if (CollectionUtils.isEmpty(history)) {
                        throw new NotFoundException("Analysis Not Found");
                    }

                    var analysis = genAiClient.getGenAiStockAnalysis(symbol, history, mongoConfigService.getConfig().getGoogleAuth().getGeminiKey());
                    if (StringUtils.isEmpty(analysis)) {
                        throw new NotFoundException("Analysis Not Found");
                    }

                    var res = HelperUtil.GSON.fromJson(analysis, AIAnalysis.class);
                    stringRedisTemplate.opsForValue().set(cacheKey, analysis,
                            DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(10)));
                    return ResponseEntity.ok(ApiResponse.ok(res, "Ai Analysis Fetched Successfully"));
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.warn("Could not acquire lock for symbol: {}, server is busy", symbol);
                throw new NotFoundException("Analysis Not Found");
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new NotFoundException("Analysis Not Found");
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while getting GenAiStockAnalysis data", e);
            throw new NotFoundException("Analysis Not Found");
        }
    }

    @Override
    @Async("taskExecutor")
    public void updateStrategyBacktestData() {
        var body = strategyService.getAllStrategies().getBody();
        var activeStrategies = body != null ? body.getData() : null;
        if (CollectionUtils.isEmpty(activeStrategies)) {
            return;
        }

        Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData = new HashMap<>();
        var stopDate = DateUtil.getTodayDate().minusDays(30);

        for (var strategy : activeStrategies) {
            processStrategyBacktest(strategy, historicalData, stopDate);
        }

        strategyService.refreshStrategyCache();
    }

    private void processStrategyBacktest(
            StrategyDto strategy,
            Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData,
            LocalDate stopDate) {
        var backtestResults = chartInkService.fetchBacktestDataWithMargin(strategy.getName());
        if (CollectionUtils.isEmpty(backtestResults)) {
            return;
        }

        var stats = new TradeStats();
        for (int i = backtestResults.size() - 1; i >= 0; i--) {
            var tradeData = backtestResults.get(i);
            var tradeDate = tradeData.getMarketTime().toLocalDate();
            if (tradeDate.isBefore(stopDate)) {
                break;
            }

            for (var trade : tradeData.getMargins()) {
                evaluateTrade(trade, tradeDate, historicalData, stats);
            }
        }

        log.info("Trade Count: {} for Strategy: {} with Success: {}", stats.tradeCount, strategy.getName(), stats.success);
        float successRate = ((float) stats.success / stats.tradeCount) * 100;
        Query query = new Query(Criteria.where(Strategy.Fields.name).is(strategy.getName()));
        Update update = new Update();
        update.set(Strategy.Fields.successRate, successRate);
        mongoTemplate.updateFirst(query, update, Strategy.class);
    }

    private void evaluateTrade(
            Margin trade,
            LocalDate tradeDate,
            Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData,
            TradeStats stats) {
        var symbol = trade.getSymbol();
        var stockHistory = getOrFetchHistoricalData(symbol, trade.getToken(), historicalData);
        if (CollectionUtils.isEmpty(stockHistory)) {
            return;
        }

        var candle = stockHistory.get(tradeDate);
        if (candle == null) {
            return;
        }

        stats.tradeCount++;
        if ((candle.open() * 1.006) <= candle.high()) {
            stats.success++;
        }
    }

    private Map<LocalDate, SmartApiLtpResponse.CandleDetail> getOrFetchHistoricalData(
            String symbol,
            String token,
            Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData) {
        if (historicalData.containsKey(symbol)) {
            return historicalData.get(symbol);
        }

        try {
            Thread.sleep(Duration.ofSeconds(1).toMillis());
            Map<LocalDate, SmartApiLtpResponse.CandleDetail> stockHistory =
                    angelOneService.getHistoricalData(token, symbol);
            historicalData.put(symbol, stockHistory != null ? stockHistory : Collections.emptyMap());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Sleep interrupted");
        } catch (Exception e) {
            log.error("Failed fetching data for {}: {}", symbol, e.getMessage());
            historicalData.put(symbol, Collections.emptyMap());
        }

        return historicalData.get(symbol);
    }

    private static class TradeStats {
        private int tradeCount;
        private int success;
    }

}
