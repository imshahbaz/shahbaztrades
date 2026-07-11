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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataContainer {

    private final ConcurrentHashMap<String, BarSeries> tokenSeriesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<LiveTick>> tokenTickBufferMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> activeWorkerMap = new ConcurrentHashMap<>();
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

    public Queue<LiveTick> getTickBuffer(String token) {
        return tokenTickBufferMap.computeIfAbsent(token, _ -> new ConcurrentLinkedQueue<>());
    }

    public void clear() {
        tokenSeriesMap.clear();
    }

    public void startWorkersForActiveWatchlist(BiConsumer<String, Integer> webSocketSubscriber) {
        var activeTokens = strategyRegistry.getAllActiveTokens();

        for (String token : activeTokens) {
            webSocketSubscriber.accept(token, ExchangeType.NSE.getValue());
            activeWorkerMap.computeIfAbsent(token, k -> {
                HelperUtil.EXECUTOR.execute(() -> runTokenEventLoop(k));
                return true;
            });
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

        var chartInkResult = chartInkService.fetchData("RSI15MINLOCAL");
        if (chartInkResult != null && !CollectionUtils.isEmpty(chartInkResult.getData())) {
            chartInkResult.getData().forEach(dto -> {
                var margin = marginService.getMarginCache().get(dto.getNsecode());
                if (margin != null) {
                    if (!processedTokens.contains(margin.getToken())) {
                        try {
                            Thread.sleep(Duration.ofSeconds(1));
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        }

                        var request = HistoricalDataRequest.builder()
                                .exchange("NSE")
                                .symbolToken(margin.getToken())
                                .interval("FIFTEEN_MINUTE")
                                .fromDate(fromDateStr)
                                .toDate(toDateStr)
                                .build();

                        try {
                            var angelOneResp = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, apiKey, request);
                            if (angelOneResp != null) {
                                var candles = angelOneResp.getHistoricalCandles();
                                var series = getSeries(margin.getToken());
                                for (var candle : candles) {
                                    series.addBar(buildBar(series, candle.timestamp().plusMinutes(15).toInstant(),
                                            candle.open(), candle.high(), candle.low(), candle.close()), false);
                                }
                                processedTokens.add(margin.getToken());
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            failedTokens.add(margin.getToken());
                        }
                    }

                    strategyRegistry.assignTokenToStrategy("RSI15MIN", margin.getToken(), margin.getSymbol());
                }
            });
        }

        chartInkResult = chartInkService.fetchData("MACD15MINLOCAL");
        if (chartInkResult != null && !CollectionUtils.isEmpty(chartInkResult.getData())) {
            chartInkResult.getData().forEach(dto -> {
                var margin = marginService.getMarginCache().get(dto.getNsecode());
                if (margin != null) {
                    if (!processedTokens.contains(margin.getToken())) {
                        try {
                            Thread.sleep(Duration.ofSeconds(1));
                        } catch (InterruptedException e) {
                            log.warn(e.getMessage(), e);
                        }

                        var request = HistoricalDataRequest.builder()
                                .exchange("NSE")
                                .symbolToken(margin.getToken())
                                .interval("FIFTEEN_MINUTE")
                                .fromDate(fromDateStr)
                                .toDate(toDateStr)
                                .build();

                        try {
                            var angelOneResp = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, apiKey, request);
                            if (angelOneResp != null) {
                                var candles = angelOneResp.getHistoricalCandles();
                                var series = getSeries(margin.getToken());
                                for (var candle : candles) {
                                    series.addBar(buildBar(series, candle.timestamp().plusMinutes(15).toInstant(),
                                            candle.open(), candle.high(), candle.low(), candle.close()), false);
                                }
                                processedTokens.add(margin.getToken());
                                failedTokens.remove(margin.getToken());
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                            failedTokens.add(margin.getToken());
                        }
                    }

                    strategyRegistry.assignTokenToStrategy("MACD15MIN", margin.getToken(), margin.getSymbol());
                }
            });
        }

        for (var token : failedTokens) {
            var request = HistoricalDataRequest.builder()
                    .exchange("NSE")
                    .symbolToken(token)
                    .interval("FIFTEEN_MINUTE")
                    .fromDate(fromDateStr)
                    .toDate(toDateStr)
                    .build();

            try {
                var angelOneResp = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + jwt, apiKey, request);
                if (angelOneResp != null) {
                    var candles = angelOneResp.getHistoricalCandles();
                    var series = getSeries(token);
                    for (var candle : candles) {
                        series.addBar(buildBar(series, candle.timestamp().plusMinutes(15).toInstant(),
                                candle.open(), candle.high(), candle.low(), candle.close()), false);
                    }
                }
                Thread.sleep(Duration.ofSeconds(1));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        log.info("Container Warm Up Completed");
    }

    private void runTokenEventLoop(String token) {
        Queue<LiveTick> queue = getTickBuffer(token);
        BarSeries series = getSeries(token);

        log.info("🚀 Started dedicated Virtual Thread loop for token: {}", token);

        while (true) {
            if (DateUtil.isMarketClosedForTrading()) {
                break;
            }

            LiveTick tick = queue.poll();

            if (tick == null) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
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

        activeWorkerMap.remove(token);
        log.info("🛑 Stopped worker loop for token: {}", token);
    }

    public Bar buildBar(BarSeries series, Instant endInstant, double o, double h, double l, double c) {
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
}