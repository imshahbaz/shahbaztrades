package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.chartink.ChartinkClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.chartink.ChartInkResponseDto;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
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
    public ChartInkResponseDto fetchData(StrategyDto strategy) {
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
        var strategy = strategyService.getCachedStrategies().get(strategyName);
        if (strategy == null) {
            throw new BadRequestException("Strategy " + strategyName + " not found");
        }

        var key = CHART_INK_REDIS_KEY + strategy.getName();
        var cache = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(cache)) {
            return HelperUtil.GSON.fromJson(cache, new TypeToken<List<StockMarginDto>>() {
            }.getType());
        }

        ChartInkResponseDto response = fetchData(strategy);
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

}
