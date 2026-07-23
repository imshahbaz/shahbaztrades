package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.websocket.LiveTick;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataContainer {

    private final ConcurrentHashMap<String, BarSeries> tokenSeriesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<LiveTick>> tokenTickBufferMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tokenLocks = new ConcurrentHashMap<>();
    private final Set<String> activeWorkers = ConcurrentHashMap.newKeySet();
    private final StrategyRegistry strategyRegistry;
    private final SmartApiFeignClient smartApiFeignClient;
    private final AngelOneRateLimiter angelOneRateLimiter;
    private final ChartInkService chartInkService;
    private final MarginService marginService;
    private final MongoConfigService mongoConfigService;

    public BarSeries getSeries(String token) {
        return tokenSeriesMap.computeIfAbsent(token, k -> {
            BarSeries series = new BaseBarSeriesBuilder().withName(k).build();
            series.setMaximumBarCount(200);
            return series;
        });
    }

    public BlockingQueue<LiveTick> getTickBuffer(String token) {
        return tokenTickBufferMap.computeIfAbsent(token, _ -> new LinkedBlockingQueue<>());
    }

    private ReentrantLock getLock(String token) {
        return tokenLocks.computeIfAbsent(token, _ -> new ReentrantLock());
    }

    public BarSeries snapshotSeries(String token) {
        ReentrantLock lock = getLock(token);
        lock.lock();
        try {
            BarSeries live = getSeries(token);
            BarSeries copy = new BaseBarSeriesBuilder().withName(live.getName()).build();
            for (int i = live.getBeginIndex(); i <= live.getEndIndex(); i++) {
                copy.addBar(live.getBar(i));
            }
            return copy;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        tokenSeriesMap.clear();
        tokenTickBufferMap.clear();
        tokenLocks.clear();
        activeWorkers.clear();
    }

    @Async("taskExecutor")
    public void startWorkersForActiveWatchlist(BiConsumer<String, Integer> webSocketSubscriber) {
        var activeTokens = strategyRegistry.getAllActiveTokens();

        for (String token : activeTokens) {
            webSocketSubscriber.accept(token, ExchangeType.NSE.getValue());
            if (activeWorkers.add(token)) {
                HelperUtil.EXECUTOR.execute(() -> runTokenEventLoop(token));
            }
        }
    }

    @Async("taskExecutor")
    public void warmupContainer() {
        clear();
        strategyRegistry.clearRegistry();

        var jwt = mongoConfigService.getAngelOneJwtToken();
        var apiKey = mongoConfigService.getConfig().getAngelOneConfig().getApiKey();
        LocalDate today = DateUtil.getTodayDate();
        String fromDateStr = today.atTime(9, 15).minusDays(10).format(AO_DATE_FORMATTER);
        String toDateStr = today.atTime(15, 30).format(AO_DATE_FORMATTER);
        var ctx = new WarmupContext(jwt, apiKey, fromDateStr, toDateStr);
        var processedTokens = new HashSet<String>();
        var failedTokens = new HashSet<String>();

        loadStrategyTokens("RSI15MINLOCAL", "RSI15MIN", ctx, processedTokens, failedTokens);
        loadStrategyTokens("MACD15MINLOCAL", "MACD15MIN", ctx, processedTokens, failedTokens);

        for (var token : failedTokens) {
            loadHistoricalBars(token, ctx);
        }

        log.info("Container Warm Up Completed");
    }

    private void loadStrategyTokens(String chartInkKey, String strategyName, WarmupContext ctx,
                                    HashSet<String> processedTokens, HashSet<String> failedTokens) {
        var chartInkResult = chartInkService.fetchData(chartInkKey);
        if (chartInkResult == null || CollectionUtils.isEmpty(chartInkResult.getData())) {
            return;
        }

        chartInkResult.getData().forEach(dto -> {
            var margin = marginService.getMarginCache().get(dto.getNsecode());
            if (margin == null) {
                return;
            }

            if (!processedTokens.contains(margin.getToken())) {
                if (loadHistoricalBars(margin.getToken(), ctx)) {
                    processedTokens.add(margin.getToken());
                    failedTokens.remove(margin.getToken());
                } else {
                    failedTokens.add(margin.getToken());
                }
            }

            strategyRegistry.assignTokenToStrategy(strategyName, margin.getToken(), margin.getSymbol());
        });
    }

    private boolean loadHistoricalBars(String token, WarmupContext ctx) {
        angelOneRateLimiter.acquireHistoricalData();

        var request = HistoricalDataRequest.builder()
                .exchange("NSE")
                .symbolToken(token)
                .interval("FIFTEEN_MINUTE")
                .fromDate(ctx.fromDate())
                .toDate(ctx.toDate())
                .build();

        try {
            var angelOneResp = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + ctx.jwt(), ctx.apiKey(), request);
            if (angelOneResp == null) {
                return false;
            }

            var series = getSeries(token);
            ReentrantLock lock = getLock(token);
            lock.lock();
            try {
                for (var candle : angelOneResp.getHistoricalCandles()) {
                    series.addBar(buildBar(series, candle.timestamp().plusMinutes(15).toInstant(),
                            candle.open(), candle.high(), candle.low(), candle.close()), false);
                }
            } finally {
                lock.unlock();
            }

            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    private void runTokenEventLoop(String token) {
        BlockingQueue<LiveTick> queue = getTickBuffer(token);
        BarSeries series = getSeries(token);

        log.info("🚀 Started dedicated Virtual Thread loop for token: {}", token);

        ReentrantLock lock = getLock(token);
        BarState state = new BarState();

        while (!DateUtil.isMarketClosedForTrading()) {
            LiveTick tick = pollNextTick(queue);
            if (tick == null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                continue;
            }
            processTick(series, lock, state, tick);
        }

        activeWorkers.remove(token);
        log.info("🛑 Stopped worker loop for token: {}", token);
    }

    private LiveTick pollNextTick(BlockingQueue<LiveTick> queue) {
        try {
            return queue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void processTick(BarSeries series, ReentrantLock lock, BarState state, LiveTick tick) {
        ZonedDateTime tickTimeIST = tick.arrivalTime();
        if (tickTimeIST.getHour() == 9 && tickTimeIST.getMinute() < 15) {
            return;
        }

        double ltp = tick.price();
        ZonedDateTime expectedEndTime = computeBarEndTime(tickTimeIST);

        if (state.endTime != null && !expectedEndTime.equals(state.endTime)) {
            flushBar(series, lock, state);
        }

        if (state.endTime == null) {
            startBar(series, lock, state, expectedEndTime, ltp);
        } else {
            state.high = Math.max(state.high, ltp);
            state.low = Math.min(state.low, ltp);
        }

        state.close = ltp;
    }

    private ZonedDateTime computeBarEndTime(ZonedDateTime tickTimeIST) {
        int startMinute = (tickTimeIST.getMinute() / 15) * 15;
        return tickTimeIST.truncatedTo(ChronoUnit.HOURS)
                .withMinute(startMinute)
                .plusMinutes(15);
    }

    private void flushBar(BarSeries series, ReentrantLock lock, BarState state) {
        lock.lock();
        try {
            Bar finalBar = buildBar(series, state.endTime.toInstant(), state.open, state.high, state.low, state.close);
            series.addBar(finalBar, !series.isEmpty() && series.getLastBar().getEndTime().equals(state.endTime.toInstant()));
        } finally {
            lock.unlock();
        }
        state.endTime = null;
    }

    private void startBar(BarSeries series, ReentrantLock lock, BarState state, ZonedDateTime expectedEndTime, double ltp) {
        state.endTime = expectedEndTime;
        lock.lock();
        try {
            if (!series.isEmpty() && series.getLastBar().getEndTime().equals(expectedEndTime.toInstant())) {
                Bar existing = series.getLastBar();
                state.open = existing.getOpenPrice().doubleValue();
                state.high = Math.max(existing.getHighPrice().doubleValue(), ltp);
                state.low = Math.min(existing.getLowPrice().doubleValue(), ltp);
            } else {
                state.open = ltp;
                state.high = ltp;
                state.low = ltp;
            }
        } finally {
            lock.unlock();
        }
    }

    private Bar buildBar(BarSeries series, Instant endInstant, double o, double h, double l, double c) {
        return series.barBuilder()
                .timePeriod(Duration.ofMinutes(15))
                .endTime(endInstant)
                .openPrice(o)
                .highPrice(h)
                .lowPrice(l)
                .closePrice(c)
                .volume(0L)
                .build();
    }

    public boolean checkActiveWorker(String token) {
        return activeWorkers.contains(token);
    }

    private record WarmupContext(String jwt, String apiKey, String fromDate, String toDate) {
    }

    private static final class BarState {
        private double open = -1;
        private double high = -1;
        private double low = -1;
        private double close = -1;
        private ZonedDateTime endTime = null;
    }

}