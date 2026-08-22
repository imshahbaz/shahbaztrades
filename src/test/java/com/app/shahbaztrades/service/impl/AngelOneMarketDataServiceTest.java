package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import com.app.shahbaztrades.repo.redis.AngelOneHistoricalDataRedisRepo;
import com.app.shahbaztrades.repo.redis.MarketTickerRedisRepo;
import com.app.shahbaztrades.service.BrokerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REST market data and its Redis caches. */
@ExtendWith(MockitoExtension.class)
class AngelOneMarketDataServiceTest {

    @Mock
    private SmartApiFeignClient smartApiFeignClient;
    @Mock
    private AngelOneRateLimiter angelOneRateLimiter;
    @Mock
    private BrokerSession brokerSession;
    @Mock
    private AngelOneHistoricalDataRedisRepo angelOneHistoricalDataRedisRepo;
    @Mock
    private MarketTickerRedisRepo<SmartApiLtpResponse.MarketTicker> marketTickerRedisRepo;

    private AngelOneMarketDataService service;

    @BeforeEach
    void setUp() {
        service = new AngelOneMarketDataService(smartApiFeignClient, angelOneRateLimiter, brokerSession,
                angelOneHistoricalDataRedisRepo, marketTickerRedisRepo);
    }

    private void stubCredentials() {
        lenient().when(brokerSession.jwtToken()).thenReturn("jwt");
        lenient().when(brokerSession.apiKey()).thenReturn("api-key");
    }

    private SmartApiLtpResponse.MarketTicker ticker(double ltp) {
        var ticker = new SmartApiLtpResponse.MarketTicker();
        ticker.setLtp(ltp);
        ticker.setSymbolToken("11536");
        return ticker;
    }

    // --- market ticker ----------------------------------------------------

    @Test
    void getMarketTicker_servesTheRedisCacheWithoutCallingTheBroker() {
        var cached = ticker(3200.0);
        when(marketTickerRedisRepo.get("11536")).thenReturn(cached);

        assertSame(cached, service.getMarketTicker("11536"));

        verify(smartApiFeignClient, never()).getMultipleLtp(anyString(), anyString(), any(SmartApiLtpDto.class));
    }

    @Test
    void getMarketTicker_fetchesAndCachesOnAMiss() {
        stubCredentials();
        when(marketTickerRedisRepo.get("11536")).thenReturn(null);
        var data = new SmartApiLtpResponse.MarketData(List.of(ticker(3200.0)));
        when(smartApiFeignClient.getMultipleLtp(anyString(), eq("api-key"), any(SmartApiLtpDto.class)))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null, data));

        assertEquals(3200.0, service.getMarketTicker("11536").getLtp());

        verify(marketTickerRedisRepo).set(eq("11536"), any(SmartApiLtpResponse.MarketTicker.class),
                any(Duration.class));
    }

    @Test
    void getMarketTicker_throwsWhenTheBrokerReturnsNothingUsable() {
        stubCredentials();
        when(marketTickerRedisRepo.get("11536")).thenReturn(null);
        when(smartApiFeignClient.getMultipleLtp(anyString(), anyString(), any(SmartApiLtpDto.class)))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null,
                        new SmartApiLtpResponse.MarketData(List.of())));

        assertThrows(NotFoundException.class, () -> service.getMarketTicker("11536"));
    }

    @Test
    void getMarketTicker_throwsWhenTheBrokerCallReturnsNull() {
        stubCredentials();
        when(marketTickerRedisRepo.get("11536")).thenReturn(null);
        when(smartApiFeignClient.getMultipleLtp(anyString(), anyString(), any(SmartApiLtpDto.class)))
                .thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getMarketTicker("11536"));
    }

    // --- historical data --------------------------------------------------

    @Test
    void getHistoricalData_servesTheRedisHashKeyedByDate() {
        var candle = SmartApiLtpResponse.CandleDetail.builder()
                .timestamp(ZonedDateTime.parse("2026-08-14T09:15:00+05:30"))
                .open(100).high(105).low(99).close(104).build();
        when(angelOneHistoricalDataRedisRepo.findById("TCS")).thenReturn(Optional.of(
                AngelOneHistoricalDataRedis.builder().id("TCS").dailyHistoricalData(List.of(candle)).build()));

        var data = service.getHistoricalData("11536", "TCS");

        assertEquals(1, data.size());
        assertTrue(data.containsKey(LocalDate.of(2026, 8, 14)));
        verify(smartApiFeignClient, never())
                .getHistoricalData(anyString(), anyString(), any(HistoricalDataRequest.class));
    }

    @Test
    void getHistoricalData_fetchesRateLimitedAndPersistsOnAMiss() {
        stubCredentials();
        when(angelOneHistoricalDataRedisRepo.findById("TCS")).thenReturn(Optional.empty());
        List<List<Object>> raw = List.of(
                List.of("2026-08-14T09:15:00+05:30", 100.0, 105.0, 99.0, 104.0, 1000L));
        when(smartApiFeignClient.getHistoricalData(anyString(), eq("api-key"), any(HistoricalDataRequest.class)))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null, raw));

        var data = service.getHistoricalData("11536", "TCS");

        assertEquals(1, data.size());
        // AngelOne hard-limits historical calls; skipping the limiter gets the account blocked.
        verify(angelOneRateLimiter).acquireHistoricalData();
        verify(angelOneHistoricalDataRedisRepo).save(any(AngelOneHistoricalDataRedis.class));
    }

    @Test
    void getHistoricalData_throwsWhenTheBrokerReturnsNull() {
        stubCredentials();
        when(angelOneHistoricalDataRedisRepo.findById("TCS")).thenReturn(Optional.empty());
        when(smartApiFeignClient.getHistoricalData(anyString(), anyString(), any(HistoricalDataRequest.class)))
                .thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getHistoricalData("11536", "TCS"));
    }
}
