package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.AbstractTradingStrategy;
import com.app.shahbaztrades.service.MarginService;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

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

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macdLine = new MACDIndicator(closePrice, 5, 13);
        EMAIndicator signalLine = new EMAIndicator(macdLine, 8);

        double macd0 = macdLine.getValue(safeClosedIndex).doubleValue();
        double signal0 = signalLine.getValue(safeClosedIndex).doubleValue();
        double hist0 = macd0 - signal0;

        double macdMinus1 = macdLine.getValue(safeClosedIndex - 1).doubleValue();
        double signalMinus1 = signalLine.getValue(safeClosedIndex - 1).doubleValue();
        double histMinus1 = macdMinus1 - signalMinus1;

        double macdMinus2 = macdLine.getValue(safeClosedIndex - 2).doubleValue();
        double signalMinus2 = signalLine.getValue(safeClosedIndex - 2).doubleValue();
        double histMinus2 = macdMinus2 - signalMinus2;

        boolean isMacdUnderSignal = macd0 < signal0;

        boolean isHistTurningUp = hist0 > histMinus1;

        boolean wasHistFalling = histMinus1 < histMinus2;

        var targetBar = series.getBar(safeClosedIndex);
        boolean isGreenCandle = targetBar.getClosePrice().isGreaterThan(targetBar.getOpenPrice());

        return isMacdUnderSignal && isHistTurningUp && wasHistFalling && isGreenCandle;
    }
}
