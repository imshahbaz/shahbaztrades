package com.app.shahbaztrades.model.enums;

/** Where a strategy's intraday signals come from. */
public enum PollerType {
    /** ChartInk's hosted screener, published on a lag. */
    CHART_INK,
    /** Strategies evaluated locally against our own bar series. */
    LOCAL_STRATEGY
}
