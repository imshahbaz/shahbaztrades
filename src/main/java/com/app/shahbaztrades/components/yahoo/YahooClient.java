package com.app.shahbaztrades.components.yahoo;

import com.app.shahbaztrades.util.HttpUtil;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.dto.yahoo.YahooChartResponse;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.repo.redis.YahooMonthlyHistoricalDataRepo;
import com.app.shahbaztrades.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class YahooClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private final RestClient restClient;
    private final YahooMonthlyHistoricalDataRepo<List<NSEHistoricalData>> yahooMonthlyHistoricalDataRepo;

    public YahooClient(YahooMonthlyHistoricalDataRepo<List<NSEHistoricalData>> yahooMonthlyHistoricalDataRepo) {
        this.yahooMonthlyHistoricalDataRepo = yahooMonthlyHistoricalDataRepo;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(HttpUtil.requestFactory(Duration.ofSeconds(15)))
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<NSEHistoricalData> getMonthlyHistoricalData(String symbol) {
        List<NSEHistoricalData> cached = yahooMonthlyHistoricalDataRepo.get(symbol);
        if (cached != null) {
            return cached;
        }

        var lock = yahooMonthlyHistoricalDataRepo.getLock(symbol);
        try {
            if (!lock.tryLock(2, -1, TimeUnit.SECONDS)) {
                log.warn("Could not acquire distributed lock within 2 seconds for symbol: {}. Returning empty list.", symbol);
                return Collections.emptyList();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }

        try {
            return fetchAndCache(symbol, YahooTimeRange.RANGE_1MO.getValue());
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<NSEHistoricalData> fetchAndCache(String symbol, String timeRange) {
        try {
            List<NSEHistoricalData> cached = yahooMonthlyHistoricalDataRepo.get(symbol);
            if (cached != null) {
                return cached;
            }

            log.info("Fetching fresh historical data from Yahoo for: {}", symbol);
            YahooChartResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{symbol}.NS")
                            .queryParam("range", timeRange)
                            .queryParam("interval", "1d")
                            .build(symbol))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (_, resp) ->
                                    log.error("Yahoo API Error: {} {}", resp.getStatusCode(), resp.getStatusText()))
                    .body(YahooChartResponse.class);

            List<NSEHistoricalData> list = (response != null) ? parseResponse(symbol, response) : Collections.emptyList();
            if (!list.isEmpty()) {
                Collections.reverse(list);
                yahooMonthlyHistoricalDataRepo.set(symbol, list, DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(10)));
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