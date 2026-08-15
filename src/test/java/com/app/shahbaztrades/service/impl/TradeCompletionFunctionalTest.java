package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.PollingHelper;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.StrategyOrderService;
import com.app.shahbaztrades.service.StrategyService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Functional coverage of the trade-completion path. Unlike {@link TradeEngineImplTest}, which calls
 * the listener directly with mocks, this wires a real Spring context: the event is dispatched by the
 * container's {@code @EventListener} machinery, the watchdog is the real component, and the order
 * router is a recording double rather than a mock. That exercises the wiring
 * (watchdog -> TradeCompletionEvent -> engine -> unwatch/notify) end to end.
 *
 * <p>Async is deliberately left off so events dispatch on the calling thread and assertions stay
 * deterministic; the one genuinely async hop (HelperUtil.EXECUTOR inside the signal listener) is
 * awaited explicitly.
 */
@SpringJUnitConfig(TradeCompletionFunctionalTest.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TradeCompletionFunctionalTest {

    private static final String TOKEN = "11536";
    private static final long USER_ID = 7L;

    @Autowired
    private TradeEngineImpl tradeEngine;
    @Autowired
    private TradeWatchdog tradeWatchdog;
    @Autowired
    private RecordingOrderRouter router;
    @Autowired
    private EventRecorder events;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private StrategyOrderService strategyOrderService;
    @Autowired
    private StrategyService strategyService;
    @Autowired
    private AngelOneService angelOneService;

    @BeforeEach
    void setUp() {
        // watch() short-circuits after 15:30 IST, so the full flow needs a live session.
        Assumptions.assumeFalse(DateUtil.isSquareOffTimeReached(),
                "watchdog registration is disabled after square-off time");
    }

    // --- listener driven through the container ----------------------------

    @Test
    void aFilledExitUnwatchesTheTradeAndNotifiesTheUser() {
        var trade = watchedTrade();
        router.pendingQuantity = 0;

        publisher.publishEvent(new TradeCompletionEvent(USER_ID, trade));

        assertEquals(0, tradeWatchdog.getWatchedTradeCount(), "a filled exit must stop the watch");
        assertEquals(0, tradeWatchdog.getInFlightTriggerCount(), "the trigger must always be released");

        var notification = events.notifications.getFirst();
        assertEquals(USER_ID, notification.userId());
        assertEquals(Constants.NOTIFICATION_TITLE_SELL, notification.title());
        assertTrue(notification.body().contains("TCS"));
    }

    @Test
    void aPartiallyFilledExitKeepsTheTradeUnderWatch() {
        var trade = watchedTrade();
        router.pendingQuantity = 4;

        publisher.publishEvent(new TradeCompletionEvent(USER_ID, trade));

        assertEquals(1, tradeWatchdog.getWatchedTradeCount(), "the position is still open");
        assertTrue(events.notifications.isEmpty(), "a partial fill is not a completed sell");
        // The trigger must still be released or this token can never fire again.
        assertEquals(0, tradeWatchdog.getInFlightTriggerCount());
    }

    @Test
    void aBrokerFailureStillReleasesTheTrigger() {
        var trade = watchedTrade();
        router.failGetDetails = true;

        // The listener rethrows, so the container propagates it to the publisher.
        assertThrows(IllegalStateException.class,
                () -> publisher.publishEvent(new TradeCompletionEvent(USER_ID, trade)));

        assertEquals(0, tradeWatchdog.getInFlightTriggerCount(),
                "a stuck trigger would silently freeze this token for the rest of the session");
        assertEquals(1, tradeWatchdog.getWatchedTradeCount(), "the position is still live");
    }

    // --- full tick-to-completion flow -------------------------------------

    @Test
    void aTickThatHitsTheTargetDrivesTheWholeCompletionChain() {
        var trade = watchedTrade();
        router.pendingQuantity = 0;

        // No direct call: the watchdog observes the tick and raises the event itself.
        tradeWatchdog.onTick(TOKEN, trade.getTargetPrice() + 1);

        assertEquals(0, tradeWatchdog.getWatchedTradeCount());
        assertEquals(1, events.notifications.size());
        assertEquals("O-exit", router.detailLookups.getFirst());
    }

    @Test
    void aTickBelowTheTargetLeavesEverythingUntouched() {
        var trade = watchedTrade();

        tradeWatchdog.onTick(TOKEN, trade.getTargetPrice() - 1);

        assertEquals(1, tradeWatchdog.getWatchedTradeCount());
        assertTrue(router.detailLookups.isEmpty(), "the broker must not be polled without a target hit");
        assertTrue(events.notifications.isEmpty());
    }

    @Test
    void repeatedTicksAboveTheTargetCompleteTheTradeExactlyOnce() {
        // Ticks arrive many times a second; a second completion would double-notify the user.
        var trade = watchedTrade();
        router.pendingQuantity = 0;

        tradeWatchdog.onTick(TOKEN, trade.getTargetPrice() + 1);
        tradeWatchdog.onTick(TOKEN, trade.getTargetPrice() + 2);
        tradeWatchdog.onTick(TOKEN, trade.getTargetPrice() + 3);

        assertEquals(1, events.notifications.size());
    }

    // --- signal -> entry -> completion ------------------------------------

    @Test
    void aCompletedTradeFreesTheOrderSoTheNextSignalCanTradeAgain() {
        // Entry is placed off a matured ChartInk signal, the target is hit, and the order slot is
        // released; a later signal for the same order must be able to open a fresh position.
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of(strategyOrder()));
        when(strategyService.getCachedStrategies())
                .thenReturn(java.util.Map.of("RSI15MIN", StrategyDto.builder().name("RSI15MIN").build()));
        when(angelOneService.getLTP(anyString())).thenReturn(100.0);
        router.pendingQuantity = 0;

        tradeEngine.continuousTrade();

        tradeEngine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(maturedSignal())));
        awaitTrue(() -> tradeWatchdog.getWatchedTradeCount() == 1, "entry trade to be watched");

        // Two orders per entry: the market buy and the limit exit.
        assertEquals(2, router.placed.size());
        var watchedTarget = router.placed.getLast().getPrice();

        tradeWatchdog.onTick(TOKEN, watchedTarget + 1);
        assertEquals(0, tradeWatchdog.getWatchedTradeCount());

        tradeEngine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(maturedSignal())));
        awaitTrue(() -> router.placed.size() == 4, "a second entry after the slot was freed");
    }

    @Test
    void anUnfinishedTradeBlocksASecondEntryForTheSameOrder() {
        // The order slot stays claimed while the position is open, so duplicate signals are ignored.
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of(strategyOrder()));
        when(strategyService.getCachedStrategies())
                .thenReturn(java.util.Map.of("RSI15MIN", StrategyDto.builder().name("RSI15MIN").build()));
        when(angelOneService.getLTP(anyString())).thenReturn(100.0);

        tradeEngine.continuousTrade();

        tradeEngine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(maturedSignal())));
        awaitTrue(() -> tradeWatchdog.getWatchedTradeCount() == 1, "entry trade to be watched");

        tradeEngine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(maturedSignal())));

        assertFalseWithin(() -> router.placed.size() > 2, "no further orders may be placed");
        assertEquals(1, tradeWatchdog.getWatchedTradeCount());
    }

    // --- fixtures ---------------------------------------------------------

    /** Registers a trade with the real watchdog, mirroring what punchSingleTrade does. */
    private ActiveTrade watchedTrade() {
        var trade = ActiveTrade.builder()
                .strategyOrderId("s1").userId(USER_ID).symbol("TCS").token(TOKEN)
                .quantity(10).entryPrice(100.0).targetPrice(101.5)
                .exitOrderId("O-exit").broker(BrokerType.RUPEEZY)
                .build();
        tradeWatchdog.watch(trade);
        return trade;
    }

    private StrategyOrder strategyOrder() {
        return StrategyOrder.builder()
                .id("s1").userId(USER_ID).strategyName("RSI15MIN")
                .amount(new BigDecimal("10000")).broker(BrokerType.RUPEEZY).date(Instant.now())
                .build();
    }

    /** A signal inside the 15-23 minute maturity window the engine acts on. */
    private ChartInkBacktestMarginDto maturedSignal() {
        return ChartInkBacktestMarginDto.builder()
                .marketTime(DateUtil.getCurrentDateTime().minusMinutes(18))
                .margins(List.of(Margin.builder().symbol("TCS").token(TOKEN)
                        .requiredMargin(new BigDecimal("4.5")).rupeezyMargin(new BigDecimal("4.0")).build()))
                .build();
    }

    private void awaitTrue(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private void assertFalseWithin(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                throw new AssertionError("Expected " + description);
            }
            sleep(25);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // --- context ----------------------------------------------------------

    @Configuration
    static class TestConfig {

        @Bean
        TradeWatchdog tradeWatchdog(ApplicationEventPublisher publisher) {
            return new TradeWatchdog(publisher);
        }

        @Bean
        RecordingOrderRouter recordingOrderRouter() {
            return new RecordingOrderRouter();
        }

        @Bean
        OrderRouterFactory orderRouterFactory(List<OrderRoutingStrategy> routers) {
            return new OrderRouterFactory(routers);
        }

        @Bean
        EventRecorder eventRecorder() {
            return new EventRecorder();
        }

        @Bean
        StrategyOrderService strategyOrderService() {
            return mock(StrategyOrderService.class);
        }

        @Bean
        StrategyService strategyService() {
            return mock(StrategyService.class);
        }

        @Bean
        AngelOneService angelOneService() {
            return mock(AngelOneService.class);
        }

        @Bean
        PollingHelper pollingHelper() {
            return mock(PollingHelper.class);
        }

        @Bean
        TradeEngineImpl tradeEngine(StrategyOrderService strategyOrderService, StrategyService strategyService,
                                    ApplicationEventPublisher publisher, AngelOneService angelOneService,
                                    TradeWatchdog tradeWatchdog, OrderRouterFactory orderRouterFactory,
                                    PollingHelper pollingHelper) {
            return new TradeEngineImpl(strategyOrderService, strategyService, publisher, angelOneService,
                    tradeWatchdog, orderRouterFactory, pollingHelper);
        }
    }

    /** Captures the notifications the engine emits, so assertions read the real published events. */
    @Component
    static class EventRecorder {
        final List<NotificationRequest> notifications = new CopyOnWriteArrayList<>();

        @EventListener
        void onNotification(NotificationRequest request) {
            notifications.add(request);
        }
    }

    /** A real OrderRoutingStrategy implementation that records calls instead of hitting a broker. */
    static class RecordingOrderRouter implements OrderRoutingStrategy {

        final List<TradeOrderRequest> placed = new CopyOnWriteArrayList<>();
        final List<String> detailLookups = new CopyOnWriteArrayList<>();
        private final AtomicInteger orderSeq = new AtomicInteger();

        volatile int pendingQuantity;
        volatile boolean failGetDetails;

        @Override
        public BrokerType getBrokerType() {
            return BrokerType.RUPEEZY;
        }

        @Override
        public TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request) {
            placed.add(request);
            return TradeOrderResponse.builder().orderId("O" + orderSeq.incrementAndGet()).build();
        }

        @Override
        public TradeOrderResponse getOrderDetails(Long userId, String orderId) {
            detailLookups.add(orderId);
            if (failGetDetails) {
                throw new IllegalStateException("broker unavailable");
            }
            return TradeOrderResponse.builder()
                    .orderId(orderId).status("COMPLETE")
                    .averagePrice(new BigDecimal("100")).pendingQuantity(pendingQuantity)
                    .build();
        }

        @Override
        public TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request) {
            throw new UnsupportedOperationException("not exercised by the completion flow");
        }

        @Override
        public void updateMTFStopLossOrder(Long userId, TradeOrderRequest request) {
            throw new UnsupportedOperationException("not exercised by the completion flow");
        }

        @Override
        public void cancelOrder(Long userId, String orderId) {
            throw new UnsupportedOperationException("not exercised by the completion flow");
        }

        @Override
        public void convertSLToMarket(Long userId, TradeOrderRequest request) {
            throw new UnsupportedOperationException("not exercised by the completion flow");
        }

        @Override
        public TradeOrderResponse placePreMarketOrder(Long userId, TradeOrderRequest request) {
            throw new UnsupportedOperationException("not exercised by the completion flow");
        }
    }
}
