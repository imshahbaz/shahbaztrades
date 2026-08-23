package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.analysis.TradingViewClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.repo.redis.TvNewsRedisRepo;
import com.app.shahbaztrades.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsServiceImpl implements StockNewsService {

    private static final Duration NEWS_TTL = Duration.ofMinutes(10);

    private final TradingViewClient tradingViewClient;
    private final TvNewsRedisRepo<List<TradingViewNewsResponse.NewsItem>> tvNewsRedisRepo;

    @Override
    public List<TradingViewNewsResponse.NewsItem> getStockNews(String symbol) {
        List<TradingViewNewsResponse.NewsItem> cache = tvNewsRedisRepo.get(symbol);
        if (cache != null) {
            return cache;
        }

        var res = tradingViewClient.getStockNews(symbol);
        if (res != null && !CollectionUtils.isEmpty(res.items())) {
            tvNewsRedisRepo.set(symbol, res.items(), NEWS_TTL);
            return res.items();
        }

        throw new NotFoundException("News Not Found");
    }
}
