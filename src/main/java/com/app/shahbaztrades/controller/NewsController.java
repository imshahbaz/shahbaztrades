package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.analysis.TradingViewNewsResponse;
import com.app.shahbaztrades.service.NewsService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    @PublicEndpoint
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<TradingViewNewsResponse.NewsItem>>> getStockNews(@PathVariable @NotBlank String symbol) {
        return newsService.getStockNews(symbol);
    }

}
