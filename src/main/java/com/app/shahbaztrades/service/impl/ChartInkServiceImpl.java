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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartInkServiceImpl implements ChartInkService {

    private static final String CHART_INK_REDIS_KEY_PREFIX = "chartink_result:";
    private static final String IST_ZONE_ID = "Asia/Kolkata";
    private static final ZoneId IST_ZONE = ZoneId.of(IST_ZONE_ID);

    private final ChartinkClient chartinkClient;
    private final JsonMapper jsonMapper;
    private final MarginService marginService;
    private final StringRedisTemplate stringRedisTemplate;
    private final StrategyService strategyService;

    private final AtomicReference<String> xsrfToken = new AtomicReference<>();

    @Override
    public void refreshTokens() {
        String token = chartinkClient.fetchCsrfToken();
        if (StringUtils.isBlank(token)) {
            throw new NotFoundException("XSRF-TOKEN not found in Chartink cookies");
        }
        xsrfToken.set(token);
        log.debug("Chartink tokens refreshed successfully.");
    }

    @Override
    public ChartInkResponseDto fetchData(String strategyName) {
        String scanClause = getScanClauseOrThrow(strategyName);
        return executeWithRetry(() -> {
            String json = chartinkClient.fetchData(xsrfToken.get(), Map.of("scan_clause", scanClause));
            return jsonMapper.readValue(json, ChartInkResponseDto.class);
        });
    }

    @Override
    public List<StockMarginDto> fetchWithMargin(String strategyName) {
        String redisKey = CHART_INK_REDIS_KEY_PREFIX + strategyName;

        String cachedData = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isNotBlank(cachedData)) {
            return HelperUtil.GSON.fromJson(cachedData, new TypeToken<List<StockMarginDto>>() {
            }.getType());
        }

        ChartInkResponseDto response = fetchData(strategyName);
        if (response == null || response.getData() == null) return Collections.emptyList();

        List<StockMarginDto> result = response.getData().stream()
                .map(stock -> {
                    Margin m = marginService.getMarginCache().get(stock.getNsecode());
                    return m == null ? null : StockMarginDto.builder()
                                              .name(stock.getName())
                                              .symbol(stock.getNsecode())
                                              .margin(m.getMargin())
                                              .close(stock.getClose())
                                              .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(StockMarginDto::getMargin).reversed())
                .collect(Collectors.toList());

        stringRedisTemplate.opsForValue().set(redisKey, HelperUtil.GSON.toJson(result), DateUtil.getNseCacheExpiryTime());
        return result;
    }

    @Override
    public List<ChartInkBacktestDto> fetchBacktestData(String strategyName) {
        String scanClause = getScanClauseOrThrow(strategyName);

        return executeWithRetry(() -> {
            Map<String, String> payload = Map.of("scan_clause", scanClause, "max_rows", "65");
            String json = chartinkClient.fetchBackTestData(xsrfToken.get(), payload);
            ChartInkBacktestResponse resp = jsonMapper.readValue(json, ChartInkBacktestResponse.class);

            return mapBacktestResponse(resp);
        });
    }

    @Override
    public List<ChartInkBacktestMarginDto> fetchTodayBacktestDataWithMargin(String strategyName) {
        LocalDate today = DateUtil.getTodayDate();
        return fetchBacktestData(strategyName).stream()
                .filter(dto -> dto.getMarketTime().toLocalDate().isEqual(today))
                .map(this::enrichWithMargin)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChartInkBacktestMarginDto> fetchBacktestDataWithMargin(String strategyName) {
        return fetchBacktestData(strategyName).stream()
                .map(this::enrichWithMargin)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChartInkBacktestMarginDto enrichWithMargin(ChartInkBacktestDto dto) {
        if (dto.getStocks().isEmpty()) return null;

        List<Margin> margins = dto.getStocks().stream()
                .map(symbol -> marginService.getMarginCache().get(symbol))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Margin::getMargin).reversed())
                .collect(Collectors.toList());

        if (margins.isEmpty()) return null;

        return ChartInkBacktestMarginDto.builder()
                .marketTime(dto.getMarketTime())
                .margins(margins)
                .build();
    }

    private <T> T executeWithRetry(ChartinkAction<T> action) {
        try {
            if (xsrfToken.get() == null) refreshTokens();
            return action.apply();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 419 || e.getStatusCode().value() == 401) {
                refreshTokens();
                try {
                    return action.apply();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            throw e;
        } catch (Exception e) {
            log.warn("Request failed, attempting refresh: {}", e.getMessage());
            refreshTokens();
            try {
                return action.apply();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String getScanClauseOrThrow(String strategyName) {
        var strategy = strategyService.getCachedStrategies().get(strategyName);
        if (strategy == null) throw new BadRequestException("Strategy " + strategyName + " not found");
        return strategy.getScanClause();
    }

    private List<ChartInkBacktestDto> mapBacktestResponse(ChartInkBacktestResponse resp) {
        if (resp.getMetaData() == null || resp.getMetaData().isEmpty()) return Collections.emptyList();

        ChartInkBacktestResponse.MetaData meta = resp.getMetaData().getFirst();
        List<Long> tradeTimes = meta.getTradeTimes();
        List<List<String>> aggregatedStockList = resp.getAggregatedStockList();

        List<ChartInkBacktestDto> signals = new ArrayList<>();
        for (int i = 0; i < tradeTimes.size(); i++) {
            long ts = tradeTimes.get(i);
            long epochSec = (ts > 10_000_000_000L) ? ts / 1000 : ts;

            LocalDateTime marketTime = Instant.ofEpochSecond(epochSec)
                    .atZone(IST_ZONE)
                    .toLocalDateTime();

            List<String> stocks = new ArrayList<>();
            if (aggregatedStockList != null && i < aggregatedStockList.size()) {
                List<String> stockData = aggregatedStockList.get(i);
                for (int j = 0; j < stockData.size(); j += 3) {
                    stocks.add(stockData.get(j));
                }
            }
            signals.add(new ChartInkBacktestDto(marketTime, stocks));
        }
        return signals;
    }

    @FunctionalInterface
    private interface ChartinkAction<T> {
        T apply() throws Exception;
    }
}