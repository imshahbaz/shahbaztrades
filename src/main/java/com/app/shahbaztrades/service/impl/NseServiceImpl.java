package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.service.NseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NseServiceImpl implements NseService {

    private final YahooClient yahooClient;

    @Override
    public List<NSEHistoricalData> getHistoricalData(String symbol) {
        return yahooClient.getHistoricalData(symbol, YahooTimeRange.RANGE_1MO.getValue());
    }
}
