package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.angelone.SmartApiFeignClient;
import com.app.shahbaztrades.components.helper.MarketDataContainer;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.angelone.HistoricalDataRequest;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.angelone.websocket.AngelOneLoginResponse;
import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.redis.AngelOneHistoricalDataRedisRepo;
import com.app.shahbaztrades.repo.redis.AngelOneLoginDataRedisRepo;
import com.app.shahbaztrades.repo.redis.MarketTickerRedisRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * Covers the non-websocket surface of AngelOneServiceImpl: LTP sentinels, the ticker and
 * historical-data caches, and broker-session refresh.
 */
@ExtendWith(MockitoExtension.class)
class AngelOneServiceImplTest {

    @Mock
    private AngelOneClient angelOneClient;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private SmartApiFeignClient smartApiFeignClient;
    @Mock
    private AngelOneRateLimiter angelOneRateLimiter;
    @Mock
    private MarketDataContainer marketDataContainer;
    @Mock
    private MarketTickPipeline marketTickPipeline;
    @Mock
    private StrategyRegistry strategyRegistry;
    @Mock
    private AngelOneHistoricalDataRedisRepo angelOneHistoricalDataRedisRepo;
    @Mock
    private AngelOneLoginDataRedisRepo<AngelOneLoginResponse.LoginData> angelOneLoginDataRedisRepo;
    @Mock
    private MarketTickerRedisRepo<SmartApiLtpResponse.MarketTicker> marketTickerRedisRepo;

    private AngelOneServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AngelOneServiceImpl(JsonMapper.builder().build(), angelOneClient, mongoConfigService,
                smartApiFeignClient, angelOneRateLimiter, marketDataContainer, marketTickPipeline,
                strategyRegistry, angelOneHistoricalDataRedisRepo, angelOneLoginDataRedisRepo,
                marketTickerRedisRepo);
    }

    private void stubApiKey() {
        var angelOne = new MongoEnvConfig.AngelOneConfig();
        angelOne.setApiKey("api-key");
        angelOne.setClientId("client");
        var config = new MongoEnvConfig();
        config.setAngelOneConfig(angelOne);
        lenient().when(mongoConfigService.getConfig()).thenReturn(config);
    }

    private AngelOneLoginResponse.LoginData loginData(String jwt, String feed) {
        var data = new AngelOneLoginResponse.LoginData();
        data.setJwtToken(jwt);
        data.setFeedToken(feed);
        return data;
    }

    private SmartApiLtpResponse.MarketTicker ticker(double ltp) {
        var ticker = new SmartApiLtpResponse.MarketTicker();
        ticker.setLtp(ltp);
        ticker.setSymbolToken("11536");
        return ticker;
    }

    // --- connection state -------------------------------------------------

    @Test
    void aFreshServiceReportsDisconnectedWithNoReconnectAttempts() {
        assertFalse(service.isWebSocketConnected());
        assertEquals(0, service.getReconnectAttempts());
    }

    @Test
    void getLTP_returnsMinusTwoWhileTheSocketIsDown() {
        // -2 tells callers "no feed at all", which is different from "price not seen yet".
        assertEquals(-2, service.getLTP("11536"));
    }

    @Test
    void subscribe_failsFastWhenThereIsNoOpenSession() {
        assertThrows(BadRequestException.class, () -> service.subscribe("11536", ExchangeType.NSE.getValue()));
    }

    @Test
    void startWebSocket_isANoOpWithoutABrokerJwt() {
        when(mongoConfigService.getAngelOneJwtToken()).thenReturn(null);

        service.startWebSocket();

        assertFalse(service.isWebSocketConnected());
    }

    @Test
    void disconnect_isIdempotentAndLeavesTheServiceDisconnected() {
        service.disconnect();
        service.disconnect();

        assertFalse(service.isWebSocketConnected());
        assertEquals(-2, service.getLTP("11536"));
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
        stubApiKey();
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
        stubApiKey();
        when(marketTickerRedisRepo.get("11536")).thenReturn(null);
        when(smartApiFeignClient.getMultipleLtp(anyString(), anyString(), any(SmartApiLtpDto.class)))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null,
                        new SmartApiLtpResponse.MarketData(List.of())));

        assertThrows(NotFoundException.class, () -> service.getMarketTicker("11536"));
    }

    @Test
    void getMarketTicker_throwsWhenTheBrokerCallReturnsNull() {
        stubApiKey();
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
        assertTrue(data.containsKey(java.time.LocalDate.of(2026, 8, 14)));
        verify(smartApiFeignClient, never())
                .getHistoricalData(anyString(), anyString(), any(HistoricalDataRequest.class));
    }

    @Test
    void getHistoricalData_fetchesRateLimitedAndPersistsOnAMiss() {
        stubApiKey();
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
        stubApiKey();
        when(angelOneHistoricalDataRedisRepo.findById("TCS")).thenReturn(Optional.empty());
        when(smartApiFeignClient.getHistoricalData(anyString(), anyString(), any(HistoricalDataRequest.class)))
                .thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.getHistoricalData("11536", "TCS"));
    }

    // --- broker session ---------------------------------------------------

    @Test
    void refreshBrokerSession_reusesACachedTokenThatStillValidates() {
        stubApiKey();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(loginData("jwt-1", "feed-1"));
        when(smartApiFeignClient.getUserProfile(anyString(), eq("api-key")))
                .thenReturn(new SmartApiLtpResponse<>(true, "ok", null, new Object()));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-1");
        verify(mongoConfigService).setAngelOneFeedToken("feed-1");
        // A fresh TOTP login burns a one-time code, so it must be avoided when possible.
        verify(angelOneClient, never()).getWebsocketLogin(any());
    }

    @Test
    void refreshBrokerSession_reLogsInWhenTheCachedTokenIsRejected() {
        stubApiKey();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(loginData("stale", "feed"));
        when(smartApiFeignClient.getUserProfile(anyString(), anyString()))
                .thenReturn(new SmartApiLtpResponse<>(false, "Invalid Token", "AG8001", null));
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(loginData("jwt-2", "feed-2"));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-2");
        verify(angelOneLoginDataRedisRepo).set(eq("oneklik"),
                any(AngelOneLoginResponse.LoginData.class), any(Duration.class));
    }

    @Test
    void refreshBrokerSession_logsInFreshWhenRedisHasNothing() {
        stubApiKey();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(null);
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(loginData("jwt-3", "feed-3"));

        service.refreshBrokerSession();

        verify(mongoConfigService).setAngelOneJwtToken("jwt-3");
        verify(smartApiFeignClient, never()).getUserProfile(anyString(), anyString());
    }

    @Test
    void refreshBrokerSession_leavesTokensAloneWhenTheLoginFails() {
        stubApiKey();
        when(angelOneLoginDataRedisRepo.get("oneklik")).thenReturn(null);
        when(angelOneClient.getWebsocketLogin(any())).thenReturn(null);

        service.refreshBrokerSession();

        verify(mongoConfigService, never()).setAngelOneJwtToken(anyString());
    }
}
