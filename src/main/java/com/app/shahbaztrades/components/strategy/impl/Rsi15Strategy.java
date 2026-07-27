package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

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

        return getEntryRule(series).isSatisfied(safeClosedIndex);
    }

    private Rule getEntryRule(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        OpenPriceIndicator open = new OpenPriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        Rule isOversold = new UnderIndicatorRule(rsi, 35.0);
        Rule isGreenCandle = new OverIndicatorRule(close, open);

        return isOversold.and(isGreenCandle);
    }

}
