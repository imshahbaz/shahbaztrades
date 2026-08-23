package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;

import java.util.List;

/** Recent news for a symbol, as shown on the stock page. */
public interface StockNewsService {

    List<TradingViewNewsResponse.NewsItem> getStockNews(String symbol);
}
