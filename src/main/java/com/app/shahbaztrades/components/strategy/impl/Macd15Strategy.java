package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

@Component("MACD15MIN")
public class Macd15Strategy extends AbstractTradingStrategy {

    public Macd15Strategy(MarginService marginService) {
        super(marginService);
    }

    @Override
    public String getName() {
        return "MACD15MIN";
    }

    @Override
    protected boolean matches(BarSeries series) {
        if (series.isEmpty()) return false;

        int safeClosedIndex = lastClosedIndex(series);
        if (safeClosedIndex < 20) return false;

        return getEntryRule(series).isSatisfied(safeClosedIndex);
    }

    private Rule getEntryRule(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        MACDIndicator macdLine = new MACDIndicator(closePrice, 5, 13);
        EMAIndicator signalLine = new EMAIndicator(macdLine, 8);
        Indicator<Num> histogram = BinaryOperationIndicator.difference(macdLine, signalLine);
        PreviousValueIndicator histMinus1 = new PreviousValueIndicator(histogram, 1);
        PreviousValueIndicator histMinus2 = new PreviousValueIndicator(histogram, 2);

        Rule isMacdUnderSignal = new UnderIndicatorRule(macdLine, signalLine);
        Rule isHistTurningUp = new OverIndicatorRule(histogram, histMinus1);
        Rule wasHistFalling = new UnderIndicatorRule(histMinus1, histMinus2);
        Rule isGreenCandle = new OverIndicatorRule(closePrice, openPrice);

        return isMacdUnderSignal
                .and(isHistTurningUp)
                .and(wasHistFalling)
                .and(isGreenCandle);
    }

}
