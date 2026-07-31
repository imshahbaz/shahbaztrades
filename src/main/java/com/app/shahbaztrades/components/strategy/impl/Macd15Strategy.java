package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
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
        int safeClosedIndex = lastClosedIndex(series);
        int availableBars = safeClosedIndex - series.getBeginIndex() + 1;
        if (availableBars < 28) return false;

        return applyEntryRule(series, safeClosedIndex);
    }

    private boolean applyEntryRule(BarSeries series, int safeClosedIndex) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);

        Rule isGreenCandle = new OverIndicatorRule(closePrice, openPrice);
        if (!isGreenCandle.isSatisfied(safeClosedIndex)) {
            return false;
        }

        MACDIndicator macdLine = new MACDIndicator(closePrice, 5, 13);
        EMAIndicator signalLine = new EMAIndicator(macdLine, 8);

        Rule isMacdUnderSignal = new UnderIndicatorRule(macdLine, signalLine);
        if (!isMacdUnderSignal.isSatisfied(safeClosedIndex)) {
            return false;
        }

        Indicator<Num> histogram = BinaryOperationIndicator.difference(macdLine, signalLine);
        PreviousValueIndicator histMinus1 = new PreviousValueIndicator(histogram, 1);

        Rule isHistTurningUp = new OverIndicatorRule(histogram, histMinus1);
        if (!isHistTurningUp.isSatisfied(safeClosedIndex)) {
            return false;
        }

        PreviousValueIndicator histMinus2 = new PreviousValueIndicator(histogram, 2);
        Rule wasHistFalling = new UnderIndicatorRule(histMinus1, histMinus2);
        return wasHistFalling.isSatisfied(safeClosedIndex);
    }

}
