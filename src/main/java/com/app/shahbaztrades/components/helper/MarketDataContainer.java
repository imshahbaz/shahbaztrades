package com.app.shahbaztrades.components.helper;

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
import java.util.function.BiConsumer;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataContainer {

    private final ConcurrentHashMap<String, BarSeries> tokenSeriesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<LiveTick>> tokenTickBufferMap = new ConcurrentHashMap<>();
    private final Set<String> activeWorkers = ConcurrentHashMap.newKeySet();
    private final StrategyRegistry strategyRegistry;
    private final SmartApiFeignClient smartApiFeignClient;
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

    public void clear() {
        tokenSeriesMap.clear();
        tokenTickBufferMap.clear();
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
        var processedTokens = new HashSet<String>();
        var failedTokens = new HashSet<String>();

        loadStrategyTokens("RSI15MINLOCAL", "RSI15MIN", jwt, apiKey, fromDateStr, toDateStr,
                processedTokens, failedTokens);
        loadStrategyTokens("MACD15MINLOCAL", "MACD15MIN", jwt, apiKey, fromDateStr, toDateStr,
                processedTokens, failedTokens);

        for (var token : failedTokens) {
            loadHistoricalBars(token, jwt, apiKey, fromDateStr, toDateStr);
            sleepOneSecond();
        }

        log.info("Container Warm Up Completed");
    }

    private void loadStrategyTokens(String chartInkKey, String strategyName, String jwt, String apiKey,
                                    String fromDate, String toDate,
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
                sleepOneSecond();
                if (loadHistoricalBars(margin.getToken(), jwt, apiKey, fromDate, toDate)) {
                    processedTokens.add(margin.getToken());
                    failedTokens.remove(margin.getToken());
                } else {
                    failedTokens.add(margin.getToken());
                }
            }

            strategyRegistry.assignTokenToStrategy(strategyName, margin.getToken(), margin.getSymbol());
        });
    }

    private boolean loadHistoricalBars(String token, String jwt, String apiKey,
                                       String fromDate, String toDate) {
        var request = HistoricalDataRequest.builder()
                .exchange("NSE")
                .symbolToken(token)
                .interval("FIFTEEN_MINUTE")
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        try {
            var angelOneResp = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, apiKey, request);
            if (angelOneResp == null) {
                return false;
            }
            var series = getSeries(token);
            for (var candle : angelOneResp.getHistoricalCandles()) {
                series.addBar(buildBar(series, candle.timestamp().plusMinutes(15).toInstant(),
                        candle.open(), candle.high(), candle.low(), candle.close()), false);
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private void sleepOneSecond() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(e.getMessage(), e);
        }
    }

    private void runTokenEventLoop(String token) {
        BlockingQueue<LiveTick> queue = getTickBuffer(token);
        BarSeries series = getSeries(token);

        log.info("🚀 Started dedicated Virtual Thread loop for token: {}", token);

        while (true) {
            if (DateUtil.isMarketClosedForTrading()) {
                break;
            }

            LiveTick tick;
            try {
                tick = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (tick == null) {
                continue;
            }

            double ltp = tick.price();
            ZonedDateTime tickTimeIST = tick.arrivalTime();

            if (tickTimeIST.getHour() == 9 && tickTimeIST.getMinute() < 15) {
                continue;
            }

            int minute = tickTimeIST.getMinute();
            int startMinute = (minute / 15) * 15;
            ZonedDateTime barEndTimeIST = tickTimeIST.truncatedTo(ChronoUnit.HOURS)
                    .withMinute(startMinute)
                    .plusMinutes(15);

            Instant barEndInstant = barEndTimeIST.toInstant();
            synchronized (series) {
                if (!series.isEmpty() && series.getLastBar().getEndTime().equals(barEndInstant)) {
                    Bar existingBar = series.getLastBar();
                    double open = existingBar.getOpenPrice().doubleValue();
                    double high = Math.max(existingBar.getHighPrice().doubleValue(), ltp);
                    double low = Math.min(existingBar.getLowPrice().doubleValue(), ltp);
                    Bar updatedBar = buildBar(series, barEndInstant, open, high, low, ltp);
                    series.addBar(updatedBar, true);
                } else {
                    Bar newBar = buildBar(series, barEndInstant, ltp, ltp, ltp, ltp);
                    series.addBar(newBar, false);
                }
            }
        }

        activeWorkers.remove(token);
        log.info("🛑 Stopped worker loop for token: {}", token);
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

}