package com.shahbaz.trades.service;

import com.shahbaz.trades.model.dto.ChartInkResponseDto;
import com.shahbaz.trades.model.dto.StockMarginDto;
import com.shahbaz.trades.model.dto.StrategyDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface ChartInkService {

    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    Map<String,ChartInkResponseDto> RESPONSE_DTO_MAP = new ConcurrentHashMap<>();

    ChartInkResponseDto fetchData(StrategyDto request);

    List<StockMarginDto> fetchWithMargin(StrategyDto request);
}
