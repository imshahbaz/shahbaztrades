package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.model.entity.Margin;
import org.ta4j.core.BarSeries;

import java.util.List;
import java.util.Map;

public interface ContinuousTradingStrategy {

    String getName();

    /**
     * ChartInk screener that supplies this strategy's candidate universe. Several strategies may
     * share one screener; the warmup loads each screener once and fans its tokens out to them.
     */
    String watchlistKey();

    List<Margin> getFilteredMargins(List<BarSeries> barSeriesList, Map<String, String> tokenSymbolMap);

}