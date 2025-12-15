package com.shahbaz.trades.service.impl;

import com.google.gson.Gson;
import com.shahbaz.trades.client.ChartinkClient;
import com.shahbaz.trades.model.dto.ChartInkResponseDto;
import com.shahbaz.trades.model.dto.StockMarginDto;
import com.shahbaz.trades.model.dto.StrategyDto;
import com.shahbaz.trades.model.entity.Margin;
import com.shahbaz.trades.service.ChartInkService;
import com.shahbaz.trades.service.MarginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChartInkServiceImpl implements ChartInkService {

    private final ChartinkClient chartinkClient;
    private final Gson gson;

    @Override
    public ChartInkResponseDto fetchData(StrategyDto request) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("scan_clause", request.getScanClause());

            String res = chartinkClient.fetchData(xsrfToken, cookie, userAgent, payload);
            return gson.fromJson(res, ChartInkResponseDto.class);

        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<StockMarginDto> fetchWithMargin(StrategyDto request) {
        ChartInkResponseDto response = fetchData(request);

        List<StockMarginDto> list = new ArrayList<>();

        for (ChartInkResponseDto.StockData stock : response.getData()) {
            Margin margin = MarginService.marginMap.get(stock.getNsecode());
            if (margin == null) {
                continue;
            }
            list.add(StockMarginDto.builder()
                    .name(stock.getName())
                    .symbol(stock.getNsecode())
                    .margin(margin.getMargin())
                    .close(stock.getClose())
                    .build());
        }
        list.sort((a, b) -> Float.compare(b.getMargin(), a.getMargin()));
        return list;
    }

}
