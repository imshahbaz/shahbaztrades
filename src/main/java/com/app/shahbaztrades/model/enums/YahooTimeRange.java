package com.app.shahbaztrades.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum YahooTimeRange {
    RANGE_1D("1d"),
    RANGE_5D("5d"),
    RANGE_1MO("1mo"),
    RANGE_3MO("3mo"),
    RANGE_6MO("6mo"),
    RANGE_1Y("1y"),
    RANGE_2Y("2y"),
    RANGE_5Y("5y"),
    RANGE_10Y("10y"),
    RANGE_YTD("ytd"),
    RANGE_MAX("max");

    private final String value;

}