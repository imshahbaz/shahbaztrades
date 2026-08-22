package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.StrategyBacktestService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyBacktestServiceImpl implements StrategyBacktestService {

    private static final int LOOKBACK_DAYS = 30;
    /** A trade counts as a win if the day's high cleared the open by this much. */
    private static final double SUCCESS_THRESHOLD = 1.006;

    private final StrategyService strategyService;
    private final ChartInkService chartInkService;
    private final MarketDataQuery marketDataQuery;
    private final MongoTemplate mongoTemplate;

    @Override
    @Async("taskExecutor")
    public void updateStrategyBacktestData() {
        var activeStrategies = strategyService.getAllStrategies(TimeFrame.DAILY);
        if (CollectionUtils.isEmpty(activeStrategies)) {
            return;
        }

        // Shared across strategies: the same symbol is often signalled by several of them.
        Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData = new HashMap<>();
        var stopDate = DateUtil.getTodayDate().minusDays(LOOKBACK_DAYS);

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
        // Newest first, stopping once the window is exhausted.
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

        if (stats.tradeCount == 0) {
            log.info("No trades evaluated for strategy {}; skipping success-rate update", strategy.getName());
            return;
        }

        log.info("Trade Count: {} for Strategy: {} with Success: {}", stats.tradeCount, strategy.getName(), stats.success);
        float successRate = ((float) stats.success / stats.tradeCount) * 100;
        Query query = new Query(Criteria.where(Strategy.Fields.name).is(strategy.getName()));
        mongoTemplate.updateFirst(query, new Update().set(Strategy.Fields.successRate, successRate), Strategy.class);
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
        if ((candle.open() * SUCCESS_THRESHOLD) <= candle.high()) {
            stats.success++;
        }
    }

    /** Caches the miss as well, so a symbol with no history is not fetched once per signal. */
    private Map<LocalDate, SmartApiLtpResponse.CandleDetail> getOrFetchHistoricalData(
            String symbol,
            String token,
            Map<String, Map<LocalDate, SmartApiLtpResponse.CandleDetail>> historicalData) {
        if (historicalData.containsKey(symbol)) {
            return historicalData.get(symbol);
        }

        try {
            Map<LocalDate, SmartApiLtpResponse.CandleDetail> stockHistory =
                    marketDataQuery.getHistoricalData(token, symbol);
            historicalData.put(symbol, stockHistory != null ? stockHistory : Collections.emptyMap());
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
