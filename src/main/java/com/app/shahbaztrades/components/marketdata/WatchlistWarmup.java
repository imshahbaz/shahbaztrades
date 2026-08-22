package com.app.shahbaztrades.components.marketdata;

import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rebuilds the day's watchlist before the session: resolves each strategy's ChartInk screener into
 * tokens, seeds their bar series from history, and registers them against the strategies.
 * <p>
 * The screener-to-strategy mapping comes from the strategy beans themselves, so adding a strategy
 * needs no change here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistWarmup {

    private final StrategyRegistry strategyRegistry;
    private final ChartInkService chartInkService;
    private final MarginService marginService;
    private final MarketDataQuery marketDataQuery;
    private final BarSeriesStore barSeriesStore;
    private final TickAggregator tickAggregator;

    @Async("taskExecutor")
    public void warmup() {
        barSeriesStore.clear();
        tickAggregator.clear();
        strategyRegistry.clearRegistry();

        var processedTokens = new HashSet<String>();
        var failedTokens = new HashSet<String>();

        strategyRegistry.strategyNamesByWatchlistKey()
                .forEach((watchlistKey, strategyNames) ->
                        loadWatchlist(watchlistKey, strategyNames, processedTokens, failedTokens));

        retryFailures(processedTokens, failedTokens);

        log.info("Container Warm Up Completed with success {} failed {}", processedTokens.size(), failedTokens.size());
    }

    private void loadWatchlist(String watchlistKey, List<String> strategyNames,
                               Set<String> processedTokens, Set<String> failedTokens) {
        var chartInkResult = chartInkService.fetchData(watchlistKey);
        if (chartInkResult == null || CollectionUtils.isEmpty(chartInkResult.getData())) {
            log.info("Watchlist {} returned no stocks", watchlistKey);
            return;
        }

        chartInkResult.getData().forEach(dto -> {
            var margin = marginService.getMarginCache().get(dto.getNsecode());
            if (margin == null || StringUtils.isAnyEmpty(margin.getSymbol(), margin.getToken())) {
                return;
            }

            // A token shared by two screeners is loaded once, then registered against both.
            if (!processedTokens.contains(margin.getToken())) {
                if (seedSeries(margin.getToken(), margin.getSymbol())) {
                    processedTokens.add(margin.getToken());
                    failedTokens.remove(margin.getToken());
                } else {
                    failedTokens.add(margin.getToken());
                }
            }

            strategyNames.forEach(strategyName ->
                    strategyRegistry.assignTokenToStrategy(strategyName, margin.getToken(), margin.getSymbol()));
        });
    }

    private void retryFailures(Set<String> processedTokens, Set<String> failedTokens) {
        for (var token : Set.copyOf(failedTokens)) {
            if (seedSeries(token, strategyRegistry.getTokenSymbolMap().get(token))) {
                processedTokens.add(token);
                failedTokens.remove(token);
            }
        }
    }

    /**
     * @return true once the token has been seeded. An empty history still counts as seeded, so a
     * newly listed scrip is not retried against the rate-limited history API for nothing.
     */
    private boolean seedSeries(String token, String symbol) {
        try {
            barSeriesStore.appendHistory(token, marketDataQuery.getFifteenMinuteCandles(token, symbol));
            return true;
        } catch (Exception e) {
            log.error("Warmup failed for token {} symbol {}: {}", token, symbol, e.getMessage());
            return false;
        }
    }
}
