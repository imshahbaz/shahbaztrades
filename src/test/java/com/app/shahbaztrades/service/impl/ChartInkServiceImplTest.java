package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.chartink.ChartinkClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.chartink.StockMarginDto;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.repo.redis.ChartInkResultRedisRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.util.DateUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartInkServiceImplTest {

    @Mock
    private ChartinkClient chartinkClient;
    @Mock
    private MarginService marginService;
    @Mock
    private StrategyService strategyService;
    @Mock
    private ChartInkResultRedisRepo<List<StockMarginDto>> chartInkResultRedisRepo;

    private ChartInkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChartInkServiceImpl(chartinkClient, JsonMapper.builder().build(),
                marginService, strategyService, chartInkResultRedisRepo);
    }

    private void stubStrategy() {
        lenient().when(strategyService.getCachedStrategies()).thenReturn(Map.of("RSI15MIN",
                StrategyDto.builder().name("RSI15MIN").scanClause("close > 100").build()));
    }

    private Margin margin(String symbol, String required, String rupeezy) {
        return Margin.builder().symbol(symbol).name(symbol).token("t-" + symbol)
                .requiredMargin(new BigDecimal(required)).rupeezyMargin(new BigDecimal(rupeezy)).build();
    }

    /** Epoch seconds for a given IST wall-clock time on the current trading day. */
    private long istEpoch(int hour, int minute) {
        return ZonedDateTime.of(DateUtil.getTodayDate(), java.time.LocalTime.of(hour, minute),
                ZoneId.of("Asia/Kolkata")).toEpochSecond();
    }

    // --- tokens -----------------------------------------------------------

    @Test
    void csrfToken_isScrapedOnceAndReusedForLaterCalls() {
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        stubStrategy();
        when(chartinkClient.fetchData(eq("xsrf-1"), anyMap())).thenReturn("{\"data\":[]}");

        service.fetchData("RSI15MIN");
        service.fetchData("RSI15MIN");

        // The token is cached, so the cookie is only scraped for the first call.
        verify(chartinkClient).fetchCsrfToken();
        verify(chartinkClient, times(2)).fetchData(eq("xsrf-1"), anyMap());
    }

    @Test
    void fetchData_throwsWhenChartinkReturnsNoCsrfCookie() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.fetchData("RSI15MIN"));
    }

    // --- fetchData --------------------------------------------------------

    @Test
    void fetchData_looksUpTheScanClauseByUpperCaseName() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchData(anyString(), anyMap())).thenReturn("{\"data\":[]}");

        service.fetchData("rsi15min");

        verify(chartinkClient).fetchData("xsrf-1", Map.of("scan_clause", "close > 100"));
    }

    @Test
    void fetchData_throwsForAnUnknownStrategy() {
        when(strategyService.getCachedStrategies()).thenReturn(Map.of());
        assertThrows(BadRequestException.class, () -> service.fetchData("NOPE"));
    }

    @Test
    void fetchData_nullStrategyNameCurrentlyEscapesAsNpeNotBadRequest() {
        // getScanClauseOrThrow guards `strategyName == null` but then calls
        // ConcurrentHashMap.get(null), which throws NPE before the guard can help.
        // Documents today's behaviour: the caller sees a 500, not a 400.
        when(strategyService.getCachedStrategies()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        assertThrows(NullPointerException.class, () -> service.fetchData(null));
    }

    @Test
    void fetchData_refreshesTheTokenAndRetriesAfterAFailure() {
        // Chartink expires the XSRF cookie silently; one transparent retry keeps the poller alive.
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1", "xsrf-2");
        when(chartinkClient.fetchData(anyString(), anyMap()))
                .thenThrow(new RuntimeException("419"))
                .thenReturn("{\"data\":[]}");

        assertEquals(0, service.fetchData("RSI15MIN").getData().size());

        verify(chartinkClient, times(2)).fetchCsrfToken();
    }

    // --- fetchWithMargin --------------------------------------------------

    @Test
    void fetchWithMargin_servesTheRedisCacheWithoutCallingChartink() {
        when(chartInkResultRedisRepo.get("RSI15MIN"))
                .thenReturn(List.of(StockMarginDto.builder().symbol("TCS").build()));

        assertEquals(1, service.fetchWithMargin("RSI15MIN").size());

        verify(chartinkClient, never()).fetchData(anyString(), anyMap());
    }

    @Test
    void fetchWithMargin_dropsSymbolsWithNoMarginAndSortsByMarginDescending() {
        stubStrategy();
        when(chartInkResultRedisRepo.get("RSI15MIN")).thenReturn(null);
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchData(anyString(), anyMap())).thenReturn("""
                {"data":[{"nsecode":"TCS","name":"Tata","close":3200.0},
                         {"nsecode":"INFY","name":"Infosys","close":1500.0},
                         {"nsecode":"UNKNOWN","name":"Nope","close":10.0}]}""");
        when(marginService.getMarginCache()).thenReturn(Map.of(
                "TCS", margin("TCS", "4.5", "4.0"),
                "INFY", margin("INFY", "6.2", "5.0")));

        List<StockMarginDto> result = service.fetchWithMargin("RSI15MIN");

        // A stock with no MTF margin cannot be traded, so it must be filtered out entirely.
        assertEquals(List.of("INFY", "TCS"), result.stream().map(StockMarginDto::getSymbol).toList());
        verify(chartInkResultRedisRepo).set(eq("RSI15MIN"), any(), any());
    }

    // --- backtest ---------------------------------------------------------

    @Test
    void fetchBacktestData_pairsTradeTimesWithEveryThirdStockColumn() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn(String.format("""
                {"metaData":[{"tradeTimes":[%d,%d]}],
                 "aggregatedStockList":[["TCS","3200","1.2","INFY","1500","0.8"],["WIPRO","400","2.0"]]}""",
                istEpoch(9, 45), istEpoch(10, 0)));

        var signals = service.fetchBacktestData("RSI15MIN");

        assertEquals(2, signals.size());
        assertEquals(List.of("TCS", "INFY"), signals.getFirst().getStocks());
        assertEquals(List.of("WIPRO"), signals.getLast().getStocks());
        assertEquals(9, signals.getFirst().getMarketTime().getHour());
        assertEquals(45, signals.getFirst().getMarketTime().getMinute());
    }

    @Test
    void fetchBacktestData_acceptsMillisecondTimestamps() {
        // Chartink switches between seconds and millis; both must land on the same wall-clock time.
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn(String.format("""
                {"metaData":[{"tradeTimes":[%d]}],"aggregatedStockList":[["TCS","3200","1.2"]]}""",
                istEpoch(11, 30) * 1000L));

        var signals = service.fetchBacktestData("RSI15MIN");

        assertEquals(11, signals.getFirst().getMarketTime().getHour());
        assertEquals(30, signals.getFirst().getMarketTime().getMinute());
    }

    @Test
    void fetchBacktestData_returnsEmptyWhenThereIsNoMetadata() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn("{\"metaData\":[]}");

        assertTrue(service.fetchBacktestData("RSI15MIN").isEmpty());
    }

    @Test
    void fetchBacktestDataWithMargin_dropsSignalsWhoseStocksHaveNoMargin() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn(String.format("""
                {"metaData":[{"tradeTimes":[%d,%d]}],
                 "aggregatedStockList":[["TCS","3200","1.2"],["UNKNOWN","1","1"]]}""",
                istEpoch(9, 45), istEpoch(10, 0)));
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", margin("TCS", "4.5", "4.0")));

        var signals = service.fetchBacktestDataWithMargin("RSI15MIN");

        assertEquals(1, signals.size());
        assertEquals("TCS", signals.getFirst().getMargins().getFirst().getSymbol());
    }

    @Test
    void fetchBacktestDataWithMargin_sortsMarginsHighestFirst() {
        stubStrategy();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn(String.format("""
                {"metaData":[{"tradeTimes":[%d]}],
                 "aggregatedStockList":[["TCS","3200","1.2","INFY","1500","0.8"]]}""", istEpoch(9, 45)));
        when(marginService.getMarginCache()).thenReturn(Map.of(
                "TCS", margin("TCS", "4.5", "4.0"),
                "INFY", margin("INFY", "6.2", "5.0")));

        var margins = service.fetchBacktestDataWithMargin("RSI15MIN").getFirst().getMargins();

        // The engine takes the first affordable stock, so the highest leverage must come first.
        assertEquals("INFY", margins.getFirst().getSymbol());
    }

    @Test
    void fetchTodayBacktestDataWithMargin_keepsOnlyTodaysSignals() {
        stubStrategy();
        long yesterday = istEpoch(9, 45) - java.time.Duration.ofDays(1).toSeconds();
        when(chartinkClient.fetchCsrfToken()).thenReturn("xsrf-1");
        when(chartinkClient.fetchBackTestData(anyString(), anyMap())).thenReturn(String.format("""
                {"metaData":[{"tradeTimes":[%d,%d]}],
                 "aggregatedStockList":[["TCS","3200","1.2"],["TCS","3200","1.2"]]}""",
                yesterday, istEpoch(10, 0)));
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", margin("TCS", "4.5", "4.0")));

        var signals = service.fetchTodayBacktestDataWithMargin("RSI15MIN");

        assertEquals(1, signals.size());
        assertEquals(DateUtil.getTodayDate(), signals.getFirst().getMarketTime().toLocalDate());
    }
}
