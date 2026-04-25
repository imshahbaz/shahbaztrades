package com.app.shahbaztrades.model.dto.strategy;

import com.app.shahbaztrades.model.entity.Margin;

public record TargetStockResult(Margin margin, int qty) {
    public String getSymbol() {
        return margin != null ? margin.getSymbol() : null;
    }
}