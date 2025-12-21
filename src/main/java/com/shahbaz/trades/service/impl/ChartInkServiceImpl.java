package com.shahbaz.trades.service.impl;

import com.google.gson.Gson;
import com.shahbaz.trades.client.ChartinkClient;
import com.shahbaz.trades.model.dto.StockMarginDto;
import com.shahbaz.trades.model.dto.StrategyDto;
import com.shahbaz.trades.model.dto.response.ChartInkResponseDto;
import com.shahbaz.trades.model.entity.Margin;
import com.shahbaz.trades.service.ChartInkService;
import com.shahbaz.trades.service.MarginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartInkServiceImpl implements ChartInkService {

    private final ChartinkClient chartinkClient;
    private final Gson gson;

    private String xsrfToken;
    private String cookie;

    @Override
    public ChartInkResponseDto fetchData(StrategyDto request) {
        try {
            if (xsrfToken == null || cookie == null) {
                refreshTokens();
            }
            Map<String, String> payload = new HashMap<>();
            payload.put("scan_clause", request.getScanClause());

            String res = chartinkClient.fetchData(xsrfToken, cookie, userAgent, payload);
            ChartInkResponseDto dto = gson.fromJson(res, ChartInkResponseDto.class);
            ChartInkService.RESPONSE_DTO_CACHE.put(request.getName(), dto);
            return dto;

        } catch (Exception e) {
            log.error("Error fetching data, retrying with fresh tokens", e);
            try {
                refreshTokens();
                Map<String, String> payload = new HashMap<>();
                payload.put("scan_clause", request.getScanClause());
                String res = chartinkClient.fetchData(xsrfToken, cookie, userAgent, payload);
                return gson.fromJson(res, ChartInkResponseDto.class);
            } catch (Exception ex) {
                log.error("Error fetching data after retry", ex);
                return null;
            }
        }
    }

    private void refreshTokens() {
        try {
            org.springframework.http.ResponseEntity<String> response = chartinkClient.getHomepage();
            List<String> cookies = response.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE);

            if (cookies != null) {
                StringBuilder cookieBuilder = new StringBuilder();
                for (String c : cookies) {
                    // Extract XSRF-TOKEN
                    if (c.startsWith("XSRF-TOKEN")) {
                        String[] parts = c.split(";");
                        if (parts.length > 0) {
                            String tokenPart = parts[0];
                            String[] tokenSplit = tokenPart.split("=");
                            if (tokenSplit.length > 1) {
                                this.xsrfToken = java.net.URLDecoder.decode(tokenSplit[1],
                                        java.nio.charset.StandardCharsets.UTF_8);
                            }
                            cookieBuilder.append(tokenPart).append("; ");
                        }
                    }
                    // Extract ci_session or laravel_session
                    if (c.startsWith("ci_session") || c.startsWith("laravel_session")) {
                        String[] parts = c.split(";");
                        if (parts.length > 0) {
                            cookieBuilder.append(parts[0]).append("; ");
                        }
                    }
                }
                this.cookie = cookieBuilder.toString();
                log.info("Refreshed tokens. XSRF: {}, Cookie: {}", this.xsrfToken, this.cookie);
            }
        } catch (Exception e) {
            log.error("Failed to refresh tokens", e);
        }
    }

    @Override
    public List<StockMarginDto> fetchWithMargin(StrategyDto request) {
        ChartInkResponseDto response = ChartInkService.RESPONSE_DTO_CACHE.getIfPresent(request.getName());

        if (response == null) {
            response = fetchData(request);
        }

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
