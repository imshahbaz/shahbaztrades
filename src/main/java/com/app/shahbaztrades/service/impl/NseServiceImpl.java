package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.market.Candle;
import com.app.shahbaztrades.service.NseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NseServiceImpl implements NseService {

    private final YahooClient yahooClient;

    @Override
    public List<Candle> getHistoricalData(String symbol) {
        return yahooClient.getMonthlyHistoricalData(symbol);
    }
}
