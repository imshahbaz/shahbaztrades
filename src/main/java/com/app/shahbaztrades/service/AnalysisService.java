package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.analysis.AIAnalysis;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;

import java.util.List;

public interface AnalysisService {

    String NEWS_FETCHED_SUCCESS_MSG = "News Fetched Successfully";

    List<TradingViewNewsResponse.NewsItem> getStockNews(String symbol);

    AIAnalysis getGenAiAnalysis(String symbol);

    void updateStrategyBacktestData();
}
