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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.app.shahbaztrades.util.Constants.AO_DATE_FORMATTER;
import static com.app.shahbaztrades.util.Constants.AO_FIFTEEN_MINUTE_INTERVAL;
import static com.app.shahbaztrades.util.Constants.AO_ONE_DAY_INTERVAL;
import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

/** REST-side AngelOne market data, cached in Redis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AngelOneMarketDataService implements MarketDataQuery {

    private static final String OHLC_MODE = "OHLC";
    private static final int DAILY_WINDOW_DAYS = 30;
    private static final int INTRADAY_WINDOW_DAYS = 10;

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
        var cached = angelOneHistoricalDataRedisRepo.findById(symbol);
        if (cached.isPresent() && !CollectionUtils.isEmpty(cached.get().getDailyHistoricalData())) {
            return byDate(cached.get().getDailyHistoricalData());
        }

        var today = DateUtil.getTodayDate();
        var candles = fetchAndCache(token, symbol, AO_ONE_DAY_INTERVAL,
                today.atTime(0, 0).minusDays(DAILY_WINDOW_DAYS).format(AO_DATE_FORMATTER),
                today.atTime(23, 59).format(AO_DATE_FORMATTER),
                cached, AngelOneHistoricalDataRedis::setDailyHistoricalData);

        if (candles == null) {
            throw new NotFoundException("Historical data not found");
        }

        return byDate(candles);
    }

    @Override
    public List<SmartApiLtpResponse.CandleDetail> getFifteenMinuteCandles(String token, String symbol) {
        var cached = angelOneHistoricalDataRedisRepo.findById(symbol);
        if (cached.isPresent() && !CollectionUtils.isEmpty(cached.get().getFifteenMinuteHistoricalData())) {
            return cached.get().getFifteenMinuteHistoricalData();
        }

        var today = DateUtil.getTodayDate();
        var candles = fetchAndCache(token, symbol, AO_FIFTEEN_MINUTE_INTERVAL,
                today.atTime(9, 15).minusDays(INTRADAY_WINDOW_DAYS).format(AO_DATE_FORMATTER),
                today.atTime(15, 30).format(AO_DATE_FORMATTER),
                cached, AngelOneHistoricalDataRedis::setFifteenMinuteHistoricalData);

        if (candles == null) {
            throw new NotFoundException("Historical data not found");
        }

        return candles;
    }

    /**
     * Fetches one interval and writes it back onto the shared per-symbol Redis entry, leaving the
     * other interval's candles on that entry untouched.
     *
     * @return the candles, or null when the broker returned nothing.
     */
    private List<SmartApiLtpResponse.CandleDetail> fetchAndCache(
            String token, String symbol, String interval, String fromDate, String toDate,
            Optional<AngelOneHistoricalDataRedis> existing,
            BiConsumer<AngelOneHistoricalDataRedis, List<SmartApiLtpResponse.CandleDetail>> assign) {

        var request = HistoricalDataRequest.builder()
                .exchange(ExchangeType.NSE.name())
                .symbolToken(token)
                .interval(interval)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        angelOneRateLimiter.acquireHistoricalData();
        var response = smartApiFeignClient.getHistoricalData(BEARER_PREFIX + brokerSession.jwtToken(),
                brokerSession.apiKey(), request);
        if (response == null) {
            return null;
        }

        var candles = response.getHistoricalCandles();
        var entry = existing.orElseGet(() -> AngelOneHistoricalDataRedis.builder().id(symbol)
                .ttl(DateUtil.getDurationUntilMarketOpen(Duration.ofHours(1)).getSeconds()).build());
        assign.accept(entry, candles);
        angelOneHistoricalDataRedisRepo.save(entry);

        return candles;
    }

    private Map<LocalDate, SmartApiLtpResponse.CandleDetail> byDate(List<SmartApiLtpResponse.CandleDetail> candles) {
        return candles.stream().collect(Collectors.toMap(
                candle -> candle.timestamp().toLocalDate(),
                candle -> candle
        ));
    }
}
