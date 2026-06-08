package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.analysis.TradingViewClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.service.NewsService;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {

    private final StringRedisTemplate stringRedisTemplate;

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
}
