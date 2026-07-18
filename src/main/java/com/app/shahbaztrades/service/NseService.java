package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;

import java.util.List;

public interface NseService {

    List<NSEHistoricalData> getHistoricalData(String symbol);
}
