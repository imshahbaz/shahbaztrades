package com.app.shahbaztrades.components;

import com.app.shahbaztrades.components.angelone.AngelOneRateLimiter;
import com.app.shahbaztrades.components.observer.MarketTickPipeline;
import com.app.shahbaztrades.components.observer.TickEvent;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.components.scheduler.ScheduledTaskExecutor;
import com.app.shahbaztrades.components.scheduler.SchedulerTask;
import com.app.shahbaztrades.components.strategy.AbstractContinuousTradingStrategy;
import com.app.shahbaztrades.components.strategy.DailyTradingStrategy;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.service.MarginService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentUnitTest {

    // --- order routing ----------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class RouterFactory {

        @Mock
        private OrderRoutingStrategy zerodha;
        @Mock
        private OrderRoutingStrategy rupeezy;

        @Test
        void getRouter_resolvesEachRegisteredBroker() {
            when(zerodha.getBrokerType()).thenReturn(BrokerType.ZERODHA);
            when(rupeezy.getBrokerType()).thenReturn(BrokerType.RUPEEZY);
            var factory = new OrderRouterFactory(List.of(zerodha, rupeezy));

            assertSame(zerodha, factory.getRouter(BrokerType.ZERODHA));
            assertSame(rupeezy, factory.getRouter(BrokerType.RUPEEZY));
        }

        @Test
        void getRouter_failsLoudlyForAnUnsupportedBroker() {
            // Silently returning null here would NPE deep inside order placement.
            when(zerodha.getBrokerType()).thenReturn(BrokerType.ZERODHA);
            var factory = new OrderRouterFactory(List.of(zerodha));

            assertThrows(NotFoundException.class, () -> factory.getRouter(BrokerType.RUPEEZY));
        }
    }

    // --- broker status mapping -------------------------------------------

    @Nested
    class EntryStatusMapping {

        @Test
        void completedStatusesMapToBought() {
            assertEquals(OrderStatus.BOUGHT, DailyTradingStrategy.mapEntryStatus("COMPLETE"));
            assertEquals(OrderStatus.BOUGHT, DailyTradingStrategy.mapEntryStatus("EXECUTED"));
            assertEquals(OrderStatus.BOUGHT, DailyTradingStrategy.mapEntryStatus("complete"));
        }

        @Test
        void rejectedMapsToRejectedAndCancelledMapsToFailed() {
            assertEquals(OrderStatus.REJECTED, DailyTradingStrategy.mapEntryStatus("REJECTED"));
            assertEquals(OrderStatus.FAILED, DailyTradingStrategy.mapEntryStatus("CANCELLED"));
            assertEquals(OrderStatus.FAILED, DailyTradingStrategy.mapEntryStatus("CANCELLED AMO"));
        }

        @Test
        void unknownAndBlankStatusesStayPlaced() {
            // "OPEN"/"TRIGGER PENDING" mean the order is still live, not finished.
            assertEquals(OrderStatus.PLACED, DailyTradingStrategy.mapEntryStatus("OPEN"));
            assertEquals(OrderStatus.PLACED, DailyTradingStrategy.mapEntryStatus(null));
            assertEquals(OrderStatus.PLACED, DailyTradingStrategy.mapEntryStatus("  "));
        }
    }

    // --- continuous strategy filtering ------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ContinuousStrategyFiltering {

        @Mock
        private MarginService marginService;

        /** Test double with a fixed entry-rule verdict, so only the filtering logic is exercised. */
        private static class AlwaysMatching extends AbstractContinuousTradingStrategy {
            private final boolean result;

            AlwaysMatching(MarginService marginService, boolean result) {
                super(marginService);
                this.result = result;
            }

            @Override
            public String getName() {
                return "TEST";
            }

            @Override
            protected boolean matches(BarSeries series) {
                return result;
            }
        }

        private BarSeries series(String token) {
            return new BaseBarSeriesBuilder().withName(token).build();
        }

        private Margin margin(String symbol, String required) {
            return Margin.builder().symbol(symbol).token("t-" + symbol)
                    .requiredMargin(new BigDecimal(required)).build();
        }

        @Test
        void getFilteredMargins_returnsMatchesSortedByMarginDescending() {
            when(marginService.getMarginCache()).thenReturn(Map.of(
                    "TCS", margin("TCS", "4.5"), "INFY", margin("INFY", "6.2")));

            var result = new AlwaysMatching(marginService, true).getFilteredMargins(
                    List.of(series("11536"), series("1594")),
                    Map.of("11536", "TCS", "1594", "INFY"));

            assertEquals(List.of("INFY", "TCS"), result.stream().map(Margin::getSymbol).toList());
        }

        @Test
        void getFilteredMargins_returnsEmptyWhenTheEntryRuleNeverFires() {
            when(marginService.getMarginCache()).thenReturn(Map.of("TCS", margin("TCS", "4.5")));

            assertTrue(new AlwaysMatching(marginService, false)
                    .getFilteredMargins(List.of(series("11536")), Map.of("11536", "TCS")).isEmpty());
        }

        @Test
        void getFilteredMargins_dropsTokensWithNoSymbolOrNoMargin() {
            // A token whose symbol was evicted from the margin cache is not tradeable.
            when(marginService.getMarginCache()).thenReturn(Map.of());

            assertTrue(new AlwaysMatching(marginService, true)
                    .getFilteredMargins(List.of(series("11536"), series("unmapped")),
                            Map.of("11536", "TCS")).isEmpty());
        }
    }

    // --- disruptor pipeline -----------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class TickPipeline {

        @Mock
        private TradeWatchdog tradeWatchdog;

        @Test
        void publish_deliversEachTickToTheWatchdogExactlyOnce() {
            var pipeline = new MarketTickPipeline(tradeWatchdog);
            pipeline.start();
            try {
                pipeline.publish("11536", 3300.0);

                // Four shards each see the event; only the owning shard may act on it.
                verify(tradeWatchdog, timeout(2000)).onTick("11536", 3300.0);
            } finally {
                pipeline.shutdown();
            }
        }

        @Test
        void publish_isANoOpBeforeStartAndReportsNoCapacity() {
            var pipeline = new MarketTickPipeline(tradeWatchdog);

            pipeline.publish("11536", 3300.0);

            verify(tradeWatchdog, never()).onTick(anyString(), anyDouble());
            assertEquals(-1, pipeline.getRemainingCapacity());
        }

        @Test
        void ringBufferCapacityIsReportedOnceStarted() {
            var pipeline = new MarketTickPipeline(tradeWatchdog);
            pipeline.start();
            try {
                assertEquals(16384, pipeline.getRingBufferSize());
                assertEquals(4, pipeline.getShardCount());
                assertTrue(pipeline.getRemainingCapacity() > 0);
            } finally {
                pipeline.shutdown();
            }
        }

        @Test
        void tickEvent_isAMutableCarrier() {
            // Disruptor pre-allocates and reuses these slots, so they must be settable.
            var event = new TickEvent();
            event.setToken("11536");
            event.setLtp(3300.0);

            assertEquals("11536", event.getToken());
            assertEquals(3300.0, event.getLtp());
        }
    }

    // --- rate limiter -----------------------------------------------------

    @Nested
    class RateLimiter {

        @Test
        void acquireHistoricalData_throttlesToOneCallPer1500ms() {
            var limiter = new AngelOneRateLimiter();

            limiter.acquireHistoricalData();
            long start = System.nanoTime();
            limiter.acquireHistoricalData();
            long waitedMs = (System.nanoTime() - start) / 1_000_000;

            // AngelOne rejects historical requests faster than this; the wait must actually happen.
            assertTrue(waitedMs > 500, "expected throttling, waited only " + waitedMs + "ms");
        }
    }

    // --- scheduler task ---------------------------------------------------

    @Nested
    @ExtendWith(MockitoExtension.class)
    class SchedulerTaskDispatch {

        @Mock
        private ScheduledTaskExecutor executor;

        private SchedulerCallBackDto callback() {
            return new SchedulerCallBackDto("https://example.com", "POST", null, Map.of());
        }

        private SchedulerTask withExecutor(SchedulerTask task) {
            task.setScheduledTaskExecutor(executor);
            return task;
        }

        @Test
        void run_dispatchesACronTaskToExecuteCron() {
            var dto = new CronTaskDto("cron-1", callback(), "0 * * * * ?");

            withExecutor(new SchedulerTask(dto)).run();

            verify(executor).executeCron(dto);
            verify(executor, never()).executeTask(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void run_dispatchesAOneOffTaskToExecuteTask() {
            var dto = new ScheduledTaskDto(callback(), System.currentTimeMillis(), "t1");

            withExecutor(new SchedulerTask(dto)).run();

            verify(executor).executeTask(dto);
            verify(executor, never()).executeCron(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void run_isANoOpWhenNeitherPayloadIsSet() {
            // Redisson deserialises via the no-arg constructor; an empty task must not NPE.
            withExecutor(new SchedulerTask()).run();

            verify(executor, never()).executeCron(org.mockito.ArgumentMatchers.any());
            verify(executor, never()).executeTask(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void schedulerTaskIsSerializableSoRedissonCanShipIt() {
            assertNotNull(new SchedulerTask(new CronTaskDto("c", callback(), "0 * * * * ?")));
            assertTrue(java.io.Serializable.class.isAssignableFrom(SchedulerTask.class));
        }
    }
}
