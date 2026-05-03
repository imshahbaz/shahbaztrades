package com.app.shahbaztrades.model.dto.strategy;

public record TradeCompletionEvent(long userId, ActiveTrade trade) {
}
