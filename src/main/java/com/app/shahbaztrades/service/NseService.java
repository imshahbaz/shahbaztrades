package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.market.Candle;

import java.util.List;

public interface NseService {

    List<Candle> getHistoricalData(String symbol);
}
