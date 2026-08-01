package com.app.shahbaztrades.model.dto.order;

public record MtfTickEvent(ActiveMtfTrade trade, double ltp, double peakPrice) {
}
