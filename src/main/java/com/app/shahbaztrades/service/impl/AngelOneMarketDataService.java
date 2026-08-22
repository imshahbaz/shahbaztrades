package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.redis.AngelOneHistoricalDataRedisRepo;
import com.app.shahbaztrades.repo.redis.MarketTickerRedisRepo;
import com.app.shahbaztrades.service.BrokerSession;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.AO_ONE_DAY_INTERVAL;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

/** REST-side AngelOne market data, cached in Redis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneMarketDataService implements MarketDataQuery {

    private static final String OHLC_MODE = "OHLC";
    private static final int HISTORY_WINDOW_DAYS = 30;

    private final SmartApiFeignClient smartApiFeignClient;
    private final AngelOneRateLimiter angelOneRateLimiter;
    private final BrokerSession brokerSession;
    private final AngelOneHistoricalDataRedisRepo angelOneHistoricalDataRedisRepo;
    private final MarketTickerRedisRepo<SmartApiLtpResponse.MarketTicker> marketTickerRedisRepo;

    @Override
    public SmartApiLtpResponse.MarketTicker getMarketTicker(String token) {
        SmartApiLtpResponse.MarketTicker data = marketTickerRedisRepo.get(token);
        if (data != null) {
            return data;
        }

        var response = smartApiFeignClient.getMultipleLtp(BEARER_PREFIX + brokerSession.jwtToken(),
                brokerSession.apiKey(),
                SmartApiLtpDto.builder()
                        .mode(OHLC_MODE)
                        .exchangeTokens(Map.of(ExchangeType.NSE.name(), List.of(token)))
                        .build());

        if (response != null && response.data() != null && !CollectionUtils.isEmpty(response.data().getFetched())) {
            marketTickerRedisRepo.set(token, response.data().getFetched().getFirst(),
                    DateUtil.getDurationUntilMarketOpen(Duration.ofMinutes(1)));
            return response.data().getFetched().getFirst();
        }

        throw new NotFoundException("Ltp not found");
    }

    @Override
    public Map<LocalDate, SmartApiLtpResponse.CandleDetail> getHistoricalData(String token, String symbol) {
        var optionalData = angelOneHistoricalDataRedisRepo.findById(symbol);
        if (optionalData.isPresent()) {
            var historicalData = optionalData.get();
            if (!CollectionUtils.isEmpty(historicalData.getDailyHistoricalData())) {
                return byDate(historicalData.getDailyHistoricalData());
            }
        }

        var today = DateUtil.getTodayDate();
        var request = HistoricalDataRequest.builder()
                .exchange(ExchangeType.NSE.name())
                .symbolToken(token)
                .interval(AO_ONE_DAY_INTERVAL)
                .fromDate(today.atTime(0, 0).minusDays(HISTORY_WINDOW_DAYS).format(AO_DATE_FORMATTER))
                .toDate(today.atTime(23, 59).format(AO_DATE_FORMATTER))
                .build();

        angelOneRateLimiter.acquireHistoricalData();
        var response = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + brokerSession.jwtToken(),
                brokerSession.apiKey(), request);

        if (response != null) {
            var candles = response.getHistoricalCandles();

            AngelOneHistoricalDataRedis data;
            if (optionalData.isPresent()) {
                data = optionalData.get();
                data.setDailyHistoricalData(candles);
            } else {
                data = AngelOneHistoricalDataRedis.builder().id(symbol).dailyHistoricalData(candles)
                        .ttl(DateUtil.getDurationUntilMarketOpen(Duration.ofHours(1)).getSeconds()).build();
            }
            angelOneHistoricalDataRedisRepo.save(data);

            return byDate(candles);
        }

        throw new NotFoundException("Historical data not found");
    }

    private Map<LocalDate, SmartApiLtpResponse.CandleDetail> byDate(List<SmartApiLtpResponse.CandleDetail> candles) {
        return candles.stream().collect(Collectors.toMap(
                candle -> candle.timestamp().toLocalDate(),
                candle -> candle
        ));
    }
}
