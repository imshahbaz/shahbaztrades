package com.shahbaz.trades.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.shahbaz.trades.model.dto.StockMarginDto;
import com.shahbaz.trades.model.dto.StrategyDto;
import com.shahbaz.trades.model.dto.response.ChartInkResponseDto;

import java.time.Duration;
import java.util.List;

public interface ChartInkService {

    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    Cache<String, ChartInkResponseDto> RESPONSE_DTO_CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    ChartInkResponseDto fetchData(StrategyDto request);

    List<StockMarginDto> fetchWithMargin(StrategyDto request);
}
