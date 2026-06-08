package com.app.shahbaztrades.model.dto.analysis;

import java.util.List;

public record TradingViewNewsResponse(List<NewsItem> items) {
    public record NewsItem(String title, long published) {
    }
}
