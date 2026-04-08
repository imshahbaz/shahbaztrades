package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.chartink.ChartinkClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.chartink.*;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartInkServiceImpl implements ChartInkService {

    private final ChartinkClient chartinkClient;
    private final JsonMapper jsonMapper;
    private final MarginService marginService;
    private final StringRedisTemplate stringRedisTemplate;
    private final StrategyService strategyService;
    private static final String CHART_INK_REDIS_KEY = "chartink_result_";
    private volatile String xsrfToken;

    @Override
    public synchronized void refreshTokens() {
        xsrfToken = chartinkClient.fetchCsrfToken();
        if (StringUtils.isEmpty(xsrfToken)) {
            throw new NotFoundException("XSRF-TOKEN not found in Chartink cookies");
        }
    }

    @Override
    public ChartInkResponseDto fetchData(String strategyName) {
        var strategy = strategyService.getCachedStrategies().get(strategyName);
        if (strategy == null) {
            throw new BadRequestException("Strategy " + strategyName + " not found");
        }
        try {
            String jsonResponse = executeWithRetry(strategy.getScanClause());
            return jsonMapper.readValue(jsonResponse, ChartInkResponseDto.class);
        } catch (Exception e) {
            log.error("Error fetching data from Chartink: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<StockMarginDto> fetchWithMargin(String strategyName) {
        var key = CHART_INK_REDIS_KEY + strategyName;
        var cache = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(cache)) {
            return HelperUtil.GSON.fromJson(cache, new TypeToken<List<StockMarginDto>>() {
            }.getType());
        }

        ChartInkResponseDto response = fetchData(strategyName);
        if (response == null) {
            return Collections.emptyList();
        }

        List<StockMarginDto> result = new ArrayList<>();
        for (ChartInkResponseDto.StockData stock : response.getData()) {
            Margin m = marginService.getMarginCache().get(stock.getNsecode());
            if (m != null) {
                result.add(StockMarginDto.builder()
                        .name(stock.getName())
                        .symbol(stock.getNsecode())
                        .margin(m.getMargin())
                        .close(stock.getClose())
                        .build());
            }
        }

        sortResultByMargin(result);
        stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(result), DateUtil.getNseCacheExpiryTime());

        return result;
    }

    @Override
    public List<ChartInkBacktestDto> fetchBacktestData(String strategyName) {
        var strategy = strategyService.getCachedStrategies().get(strategyName);
        if (strategy == null) {
            throw new BadRequestException("Strategy " + strategyName + " not found");
        }

        try {
            String jsonResponse = executeBacktestWithRetry(strategy.getScanClause());
            var resp = jsonMapper.readValue(jsonResponse, ChartInkBacktestResponse.class);
            List<ChartInkBacktestDto> signals = new ArrayList<>();
            ZoneId istZone = ZoneId.of("Asia/Kolkata");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            if (resp.getMetaData() != null && !resp.getMetaData().isEmpty()) {
                ChartInkBacktestResponse.MetaData meta = resp.getMetaData().getFirst();
                List<Long> tradeTimes = meta.getTradeTimes();
                List<List<String>> aggregatedStockList = resp.getAggregatedStockList();

                for (int i = 0; i < tradeTimes.size(); i++) {
                    long ts = tradeTimes.get(i);

                    if (ts > 10_000_000_000L) {
                        ts = ts / 1000;
                    }

                    String marketTime = Instant.ofEpochSecond(ts)
                            .atZone(istZone)
                            .format(formatter);

                    List<String> stocks = new ArrayList<>();
                    if (aggregatedStockList != null && i < aggregatedStockList.size()) {
                        List<String> stockData = aggregatedStockList.get(i);
                        for (int j = 0; j < stockData.size(); j += 3) {
                            stocks.add(stockData.get(j));
                        }
                    }

                    signals.add(new ChartInkBacktestDto(LocalDateTime.parse(marketTime, DateUtil.chartInkFormatter), stocks));
                }
            }
            return signals;
        } catch (Exception e) {
            log.error("Error fetching data from Chartink: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<ChartInkBacktestMarginDto> fetchBacktestDataWithMargin(String strategyName) {
        var data = fetchBacktestData(strategyName);
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        var result = new ArrayList<ChartInkBacktestMarginDto>(data.size());
        for (var dto : data) {
            if (!dto.getStocks().isEmpty()) {
                var margins = dto.getStocks().stream()
                        .map(stock -> marginService.getMarginCache().get(stock))
                        .filter(Objects::nonNull)
                        .toList();
                result.add(ChartInkBacktestMarginDto.builder()
                        .marketTime(dto.getMarketTime())
                        .margins(margins)
                        .build());
            }
        }
        return result;
    }

    @Override
    public List<ChartInkBacktestMarginDto> fetchTodayBacktestDataWithMargin(String strategyName) {
        var data = fetchBacktestData(strategyName);
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        var today = ZonedDateTime.now(DateUtil.IST_ZONE).toLocalDate();
        var result = new ArrayList<ChartInkBacktestMarginDto>(data.size());
        for (var dto : data) {
            if (!dto.getStocks().isEmpty() && dto.getMarketTime().toLocalDate().isEqual(today)) {
                var margins = dto.getStocks().stream()
                        .map(stock -> marginService.getMarginCache().get(stock))
                        .filter(Objects::nonNull)
                        .toList();
                result.add(ChartInkBacktestMarginDto.builder()
                        .marketTime(dto.getMarketTime())
                        .margins(margins)
                        .build());
            }
        }
        return result;
    }

    private String executeWithRetry(String scanClause) {
        Map<String, String> payload = Map.of("scan_clause", scanClause);
        try {
            if (StringUtils.isEmpty(xsrfToken)) {
                refreshTokens();
            }
            return chartinkClient.fetchData(xsrfToken, payload);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 419 || e.getStatusCode().value() == 401) {
                log.warn("Chartink session expired ({}). Retrying...", e.getStatusCode().value());
                refreshTokens();
                return chartinkClient.fetchData(xsrfToken, payload);
            }
            throw e;
        } catch (Exception e) {
            log.error("Request failed: {}. Retrying...", e.getMessage());
            refreshTokens();
            return chartinkClient.fetchData(xsrfToken, payload);
        }
    }

    private void sortResultByMargin(List<StockMarginDto> result) {
        result.sort(Comparator.comparingDouble(StockMarginDto::getMargin).reversed());
    }

    private String executeBacktestWithRetry(String scanClause) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("scan_clause", scanClause);
        payload.put("max_rows", "65");

        try {
            if (StringUtils.isEmpty(xsrfToken)) {
                refreshTokens();
            }
            return chartinkClient.fetchBackTestData(xsrfToken, payload);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 419 || e.getStatusCode().value() == 401) {
                log.warn("Chartink session expired ({}). Retrying...", e.getStatusCode().value());
                refreshTokens();
                return chartinkClient.fetchBackTestData(xsrfToken, payload);
            }
            throw e;
        } catch (Exception e) {
            log.error("Request failed: {}. Retrying...", e.getMessage());
            refreshTokens();
            return chartinkClient.fetchBackTestData(xsrfToken, payload);
        }
    }

}
