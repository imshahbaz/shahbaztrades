package com.app.shahbaztrades.components.yahoo;

import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.dto.yahoo.YahooChartResponse;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class YahooClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private final RestClient restClient;
    private final StringRedisTemplate stringRedisTemplate;

    public YahooClient(RestClient.Builder restClientBuilder, StringRedisTemplate stringRedisTemplate) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<NSEHistoricalData> getHistoricalData(String symbol, String timeRange) {
        var cacheKey = "yahoo_history_" + symbol + "_" + timeRange;
        var value = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(value)) {
            return HelperUtil.GSON.fromJson(value, new TypeToken<List<NSEHistoricalData>>() {
            }.getType());
        }

        log.info("Fetching fresh historical data from Yahoo for: {}", symbol);
        try {
            YahooChartResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{symbol}.NS")
                            .queryParam("range", timeRange)
                            .queryParam("interval", "1d")
                            .build(symbol))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, resp) -> {
                                log.error("Yahoo API Error: {} {}", resp.getStatusCode(), resp.getStatusText());
                            })
                    .body(YahooChartResponse.class);

            List<NSEHistoricalData> list = (response != null) ? parseResponse(symbol, response) : Collections.emptyList();
            if (!list.isEmpty()) {
                Collections.reverse(list);
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        HelperUtil.GSON.toJson(list),
                        DateUtil.getNseCacheExpiryTime()
                );
            }
            return list;

        } catch (Exception e) {
            log.error("Critical failure fetching Yahoo data for {}", symbol, e);
            return Collections.emptyList();
        }
    }

    private List<NSEHistoricalData> parseResponse(String symbol, YahooChartResponse response) {
        if (response.getChart() == null || response.getChart().getResult() == null) {
            return Collections.emptyList();
        }

        var resultData = response.getChart().getResult().getFirst();
        var timestamps = resultData.getTimestamp();
        var quote = resultData.getIndicators().getQuote().getFirst();

        List<NSEHistoricalData> list = new ArrayList<>();

        for (int i = 0; i < timestamps.size(); i++) {
            Long vol = quote.getVolume().get(i);
            Double open = quote.getOpen().get(i);

            if (vol != null && vol > 0 && open != null && open != 0) {
                list.add(NSEHistoricalData.builder()
                        .symbol(symbol)
                        .open(round(open))
                        .high(round(quote.getHigh().get(i)))
                        .low(round(quote.getLow().get(i)))
                        .close(round(quote.getClose().get(i)))
                        .timestamp(formatTimestamp(timestamps.get(i)))
                        .build());
            }
        }

        Collections.reverse(list);
        return list;
    }

    private double round(Double value) {
        return (value == null) ? 0.0 :
                BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatTimestamp(Long unixTime) {
        return Instant.ofEpochSecond(unixTime)
                .atZone(DateUtil.IST_ZONE)
                .format(DateUtil.NSE_INPUT_LAYOUT);
    }

}