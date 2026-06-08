package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface NewsService {

    ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(String symbol);
}
