package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.analysis.GenAiClient;
import com.app.shahbaztrades.components.analysis.TradingViewClient;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.NewsService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final StringRedisTemplate stringRedisTemplate;
    private final GenAiClient genAiClient;
    private final YahooClient yahooClient;
    private final MongoConfigService mongoConfigService;

    @Override
    public ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(String symbol) {
        var cacheKey = "tv_news_" + symbol;
        var value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(value)) {
            List<TradingViewNewsResponse.NewsItem> res = HelperUtil.GSON.fromJson(value, new TypeToken<List<TradingViewNewsResponse.NewsItem>>() {
            }.getType());
            return ResponseEntity.ok(ApiResponse.ok(res, "News Fetched Successfully"));
        }

        var res = TradingViewClient.getStockNews(symbol);
        if (res != null && !CollectionUtils.isEmpty(res.items())) {
            stringRedisTemplate.opsForValue().set(cacheKey, HelperUtil.GSON.toJson(res.items()));
            return ResponseEntity.ok(ApiResponse.ok(res.items(), "News Fetched Successfully"));
        }

        throw new NotFoundException("News Not Found");
    }

    @Override
    public ResponseEntity<ApiResponse<AIAnalysis>> getGenAiAnalysis(String symbol) {
        var cacheKey = "genai_" + symbol;
        var value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(value)) {
            var res = HelperUtil.GSON.fromJson(value, AIAnalysis.class);
            return ResponseEntity.ok(ApiResponse.ok(res, "News Fetched Successfully"));
        }

        var history = yahooClient.getHistoricalData(symbol, YahooTimeRange.RANGE_1MO.getValue());
        if (CollectionUtils.isEmpty(history)) {
            throw new NotFoundException("Analysis Not Found");
        }

        var analysis = genAiClient.getGenAiStockAnalysis(symbol, history, mongoConfigService.getConfig().getGoogleAuth().getGeminiKey());
        if (StringUtils.isEmpty(analysis)) {
            throw new NotFoundException("Analysis Not Found");
        }

        try {
            var res = HelperUtil.GSON.fromJson(analysis, AIAnalysis.class);
            stringRedisTemplate.opsForValue().set(cacheKey, analysis, DateUtil.getNseCacheExpiryTime());
            return ResponseEntity.ok(ApiResponse.ok(res, "Ai Analysis Fetched Successfully"));
        } catch (Exception e) {
            log.error("Error while getting GenAiStockAnalysis data", e);
        }

        throw new NotFoundException("Analysis Not Found");
    }
}
