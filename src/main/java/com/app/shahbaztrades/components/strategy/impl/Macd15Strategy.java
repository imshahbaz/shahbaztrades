package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.strategy.TradingStrategy;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component("MACD15MIN")
@RequiredArgsConstructor
public class Macd15Strategy implements TradingStrategy {

    private final MarginService marginService;

    @Override
    public String getName() {
        return "MACD15MIN";
    }

    @Override
    public List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap) {
        return barSeriesList.parallelStream()
                .map(barSeries -> {
                    String symbol = tokenSymbolMap.get(barSeries.getName());
                    if (symbol == null) return null;

                    var margin = marginService.getMarginCache().get(symbol);
                    if (margin == null) return null;

                    if (checkSetup(barSeries)) return margin;

                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(Margin::getRequiredMargin).reversed())
                .toList();
    }

    private boolean checkSetup(BarSeries series) {
        if (series.isEmpty()) return false;

        int safeClosedIndex = -1;
        for (int i = series.getEndIndex(); i >= series.getBeginIndex(); i--) {
            var candleEndTime = series.getBar(i).getEndTime().atZone(DateUtil.IST_ZONE);
            if (candleEndTime.isBefore(DateUtil.getCurrentDateTime().atZone(DateUtil.IST_ZONE))) {
                safeClosedIndex = i;
                break;
            }
        }

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
