package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Component("RSI15MIN")
public class Rsi15Strategy extends AbstractTradingStrategy {

    public Rsi15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "RSI15MIN";
    }

    @Override
    protected boolean matches(BarSeries series) {
        if (series.getBarCount() < 14) return false;

        int safeClosedIndex = lastClosedIndex(series);
        if (safeClosedIndex < 14) return false;

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        double confirmedRsi = rsi.getValue(safeClosedIndex).doubleValue();
        boolean isOversold = confirmedRsi < 35.0;

        var closedBar = series.getBar(safeClosedIndex);
        boolean isGreenCandle = closedBar.getClosePrice().isGreaterThan(closedBar.getOpenPrice());

        return isOversold && isGreenCandle;
    }
}
