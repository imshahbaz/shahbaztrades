package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.strategy.ContinuousTradingStrategy;
import com.app.shahbaztrades.components.strategy.DailyTradingStrategy;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.kronos.BulkPredictionRequestDto;
import com.app.shahbaztrades.model.dto.kronos.PredictionItemDto;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.entity.DatabaseCounter;
import com.app.shahbaztrades.model.entity.KronosPredictions;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.repo.KronosPredictionsRepo;
import com.app.shahbaztrades.service.NseService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Groups the small service implementations that need only a handful of cases each. */
class MiscServiceImplTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Nse {

        @Mock
        private YahooClient yahooClient;
        @InjectMocks
        private NseServiceImpl service;

        @Test
        void getHistoricalData_delegatesStraightToYahoo() {
            var data = List.of(NSEHistoricalData.builder().symbol("TCS").close(3200).build());
            when(yahooClient.getMonthlyHistoricalData("TCS")).thenReturn(data);

            assertSame(data, service.getHistoricalData("TCS"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SequenceGenerator {

        @Mock
        private MongoTemplate mongoTemplate;
        @InjectMocks
        private SequenceGeneratorService service;

        @Test
        void getNextSequence_returnsTheIncrementedCounter() {
            var counter = new DatabaseCounter();
            counter.setSeq(42L);
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                    any(FindAndModifyOptions.class), eq(DatabaseCounter.class))).thenReturn(counter);

            assertEquals(42L, service.getNextSequence("userid"));
        }

        @Test
        void getNextSequence_fallsBackToOneWhenTheCounterIsMissing() {
            // The very first call upserts; a null result must still yield a usable id.
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                    any(FindAndModifyOptions.class), eq(DatabaseCounter.class))).thenReturn(null);

            assertEquals(1L, service.getNextSequence("userid"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Kronos {

        @Mock
        private KronosPredictionsRepo kronosPredictionsRepo;
        @Mock
        private NseService nseService;
        @InjectMocks
        private KronosPredictionServiceImpl service;

        private PredictionItemDto item(String symbol, int horizon) {
            return PredictionItemDto.builder()
                    .symbol(symbol).runDate(LocalDate.of(2026, 8, 15)).date(LocalDate.of(2026, 8, 15 + horizon))
                    .horizonDay(horizon).open(BigDecimal.ONE).high(BigDecimal.TWO)
                    .low(BigDecimal.ZERO).close(BigDecimal.ONE)
                    .build();
        }

        @Test
        void savePredictions_groupsCandlesUnderOneDocumentPerSymbol() {
            service.savePredictions(BulkPredictionRequestDto.builder()
                    .predictions(List.of(item("TCS", 1), item("TCS", 2), item("INFY", 1)))
                    .build());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<KronosPredictions>> saved = ArgumentCaptor.forClass(Collection.class);
            verify(kronosPredictionsRepo).saveAll(saved.capture());

            var bySymbol = saved.getValue().stream()
                    .collect(java.util.stream.Collectors.toMap(KronosPredictions::getSymbol, p -> p));
            assertEquals(2, bySymbol.size());
            assertEquals(2, bySymbol.get("TCS").getPredictedCandles().size());
            assertEquals(1, bySymbol.get("INFY").getPredictedCandles().size());
        }

        @Test
        void savePredictions_isANoOpForAnEmptyRequest() {
            service.savePredictions(BulkPredictionRequestDto.builder().predictions(List.of()).build());
            service.savePredictions(BulkPredictionRequestDto.builder().build());

            verify(kronosPredictionsRepo, never()).saveAll(any());
        }

        @Test
        void getPredictions_combinesTheLatestRunWithHistoricalData() {
            when(kronosPredictionsRepo.findBySymbol(eq("TCS"), any(Pageable.class)))
                    .thenReturn(Optional.of(KronosPredictions.builder()
                            .symbol("TCS").runDate("15-Aug-2026")
                            .predictedCandles(new java.util.ArrayList<>(List.of(
                                    KronosPredictions.PredictedCandle.builder()
                                            .horizonDay(1).date("16-Aug-2026")
                                            .open(BigDecimal.ONE).high(BigDecimal.TWO)
                                            .low(BigDecimal.ZERO).close(BigDecimal.ONE).build())))
                            .build()));
            when(nseService.getHistoricalData("TCS"))
                    .thenReturn(List.of(NSEHistoricalData.builder().symbol("TCS").close(3200).build()));

            var response = service.getPredictions("TCS");

            assertEquals("TCS", response.getSymbol());
            assertEquals(1, response.getPredictions().size());
            assertEquals(1, response.getHistoricalData().size());
        }

        @Test
        void getPredictions_throwsWhenNoRunExistsForTheSymbol() {
            when(kronosPredictionsRepo.findBySymbol(eq("NOPE"), any(Pageable.class)))
                    .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.getPredictions("NOPE"));
            verify(nseService, never()).getHistoricalData(anyString());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SessionManager {

        @Mock
        private OrderService orderService;
        @Mock
        private ZerodhaService zerodhaService;
        @Mock
        private StrategyOrderService strategyOrderService;
        @Mock
        private StringRedisTemplate stringRedisTemplate;
        @Mock
        private ValueOperations<String, String> valueOperations;
        @Mock
        private UserService userService;
        @Mock
        private ApplicationEventPublisher applicationEventPublisher;
        @InjectMocks
        private SessionManagerServiceImpl service;

        @Test
        void autoConnectZerodhaSession_claimsTheInFlightFlagThenDelegates() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq(Constants.ZERODHA_AUTO_LOGIN_KEY + 7L), eq("PENDING"),
                    eq(Duration.ofMinutes(3)))).thenReturn(Boolean.TRUE);
            User user = User.builder().userId(7L).build();
            when(userService.findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong())).thenReturn(user);

            assertTrue(service.autoConnectZerodhaSession(UserDto.builder().userId(7L).build()));

            verify(zerodhaService).autoConnectZerodhaSession(user);
        }

        @Test
        void autoConnectZerodhaSession_refusesWhenAnotherRequestHoldsTheFlag() {
            // The flag is the single-flight guard around a one-shot TOTP login.
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.FALSE);

            assertThrows(ResourceAlreadyExistsException.class,
                    () -> service.autoConnectZerodhaSession(UserDto.builder().userId(7L).build()));
            verify(zerodhaService, never()).autoConnectZerodhaSession(any(User.class));
        }

        @Test
        void autoConnectZerodhaSession_treatsANullRedisReplyAsAlreadyRunning() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);

            assertThrows(ResourceAlreadyExistsException.class,
                    () -> service.autoConnectZerodhaSession(UserDto.builder().userId(7L).build()));
        }

        @Test
        void initiateZerodhaLogin_isANoOpWhenNobodyHasOrdersToday() throws Exception {
            when(orderService.getTodayOrders()).thenReturn(List.of());
            when(strategyOrderService.getTodayOrders()).thenReturn(List.of());

            service.initiateZerodhaLogin();

            verify(zerodhaService).autoLogin(java.util.Set.of());
            verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class DailyStrategyRegistry {

        @Mock
        private DailyTradingStrategy targetProfit;
        @Mock
        private DailyTradingStrategy trailingProfit;

        @Test
        void getStrategy_resolvesByName() {
            when(targetProfit.getName()).thenReturn("TARGET PROFIT");
            when(trailingProfit.getName()).thenReturn("TRAILING PROFIT");
            var registry = new DailyTradingStrategyRegistry(List.of(targetProfit, trailingProfit));

            assertSame(targetProfit, registry.getStrategy("TARGET PROFIT"));
            assertSame(trailingProfit, registry.getStrategy("TRAILING PROFIT"));
        }

        @Test
        void getStrategy_throwsForAnUnknownName() {
            when(targetProfit.getName()).thenReturn("TARGET PROFIT");
            var registry = new DailyTradingStrategyRegistry(List.of(targetProfit));

            assertThrows(NotFoundException.class, () -> registry.getStrategy("MOMENTUM"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ContinuousStrategyRegistry {

        @Mock
        private ContinuousTradingStrategy rsi;
        @Mock
        private ContinuousTradingStrategy macd;

        private StrategyRegistry registry() {
            return new StrategyRegistry(Map.of("RSI15MIN", rsi, "MACD15MIN", macd));
        }

        @Test
        void assignTokenToStrategy_recordsTheTokenAndItsSymbol() {
            var registry = registry();

            registry.assignTokenToStrategy("RSI15MIN", "11536", "TCS");

            assertEquals(List.of("11536"), registry.getTokensForStrategy("RSI15MIN"));
            assertEquals("TCS", registry.getTokenSymbolMap().get("11536"));
        }

        @Test
        void assignTokenToStrategy_isIdempotentForTheSameToken() {
            // Warmup re-runs must not subscribe the same token twice.
            var registry = registry();

            registry.assignTokenToStrategy("RSI15MIN", "11536", "TCS");
            registry.assignTokenToStrategy("RSI15MIN", "11536", "TCS");

            assertEquals(1, registry.getTokensForStrategy("RSI15MIN").size());
        }

        @Test
        void assignTokenToStrategy_rejectsAnUnregisteredStrategy() {
            assertThrows(IllegalArgumentException.class,
                    () -> registry().assignTokenToStrategy("UNKNOWN", "11536", "TCS"));
        }

        @Test
        void getAllActiveTokens_deduplicatesAcrossStrategies() {
            var registry = registry();
            registry.assignTokenToStrategy("RSI15MIN", "11536", "TCS");
            registry.assignTokenToStrategy("MACD15MIN", "11536", "TCS");
            registry.assignTokenToStrategy("MACD15MIN", "1594", "INFY");

            assertEquals(2, registry.getAllActiveTokens().size());
        }

        @Test
        void getTokensForStrategy_returnsEmptyForAnUnknownStrategy() {
            assertTrue(registry().getTokensForStrategy("NOPE").isEmpty());
        }

        @Test
        void clearRegistry_dropsTokensAndSymbols() {
            var registry = registry();
            registry.assignTokenToStrategy("RSI15MIN", "11536", "TCS");

            registry.clearRegistry();

            assertTrue(registry.getAllActiveTokens().isEmpty());
            assertTrue(registry.getTokenSymbolMap().isEmpty());
        }

        @Test
        void getStrategyInstance_returnsTheBeanOrNull() {
            var registry = registry();
            assertSame(rsi, registry.getStrategyInstance("RSI15MIN"));
            assertEquals(null, registry.getStrategyInstance("NOPE"));
        }
    }
}
