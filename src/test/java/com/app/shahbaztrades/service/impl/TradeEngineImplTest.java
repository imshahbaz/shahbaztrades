package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.PollingHelper;
import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
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
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeEngineImplTest {

    @Mock
    private StrategyOrderService strategyOrderService;
    @Mock
    private StrategyService strategyService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AngelOneService angelOneService;
    @Mock
    private TradeWatchdog tradeWatchdog;
    @Mock
    private OrderRouterFactory orderRouterFactory;
    @Mock
    private OrderRoutingStrategy orderRouter;
    @Mock
    private PollingHelper pollingHelper;

    private TradeEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new TradeEngineImpl(strategyOrderService, strategyService, eventPublisher,
                angelOneService, tradeWatchdog, orderRouterFactory, pollingHelper);
    }

    private StrategyOrder order(String id, String strategyName) {
        return StrategyOrder.builder()
                .id(id).userId(7L).strategyName(strategyName).amount(new BigDecimal("10000"))
                .broker(BrokerType.RUPEEZY).date(Instant.now())
                .build();
    }

    private ActiveTrade trade() {
        return ActiveTrade.builder()
                .strategyOrderId("s1").userId(7L).symbol("TCS").token("11536")
                .quantity(10).targetPrice(3300).exitOrderId("X1").broker(BrokerType.RUPEEZY)
                .build();
    }

    // --- continuousTrade --------------------------------------------------

    @Test
    void continuousTrade_startsOnePollerPerDistinctStrategy() {
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of(
                order("s1", "RSI15MIN"), order("s2", "RSI15MIN"), order("s3", "MACD15MIN")));
        when(strategyService.getCachedStrategies()).thenReturn(Map.of(
                "RSI15MIN", StrategyDto.builder().name("RSI15MIN").build(),
                "MACD15MIN", StrategyDto.builder().name("MACD15MIN").build()));

        engine.continuousTrade();

        // Two orders share RSI15MIN: a second poller would double-fire every signal.
        verify(pollingHelper, timeout(2000)).runPollerTask("RSI15MIN", false);
        verify(pollingHelper, timeout(2000)).runPollerTask("MACD15MIN", false);
    }

    @Test
    void continuousTrade_skipsOrdersWhoseStrategyIsNotConfigured() {
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of(order("s1", "GHOST")));
        when(strategyService.getCachedStrategies()).thenReturn(Map.of());

        engine.continuousTrade();

        verify(pollingHelper, never()).runPollerTask(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void continuousTrade_isANoOpWhenThereAreNoOrders() {
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of());

        engine.continuousTrade();

        verify(strategyService, never()).getCachedStrategies();
        verify(pollingHelper, never()).runPollerTask(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // --- signal handling --------------------------------------------------

    @Test
    void chartInkSignalListener_ignoresSignalsForAStrategyWithNoRegisteredOrders() {
        engine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(signalAt(0))));

        verify(angelOneService, never()).getLTP(anyString());
    }

    @Test
    void chartInkSignalListener_ignoresSignalsOutsideThe15To23MinuteWindow() {
        registerOrder("RSI15MIN");

        // A signal from 5 minutes ago has not yet matured; one from an hour ago is stale.
        engine.chartInkSignalListener(new ChartInkSignalEvent("RSI15MIN", List.of(signalAt(5), signalAt(60))));

        verify(angelOneService, never()).getLTP(anyString());
    }

    // --- trade completion -------------------------------------------------

    @Test
    void tradeCompletionListener_notifiesAndUnwatchesWhenTheExitIsFullyFilled() throws Exception {
        when(orderRouterFactory.getRouter(BrokerType.RUPEEZY)).thenReturn(orderRouter);
        when(orderRouter.getOrderDetails(anyLong(), anyString()))
                .thenReturn(TradeOrderResponse.builder().orderId("X1").pendingQuantity(0).build());
        var activeTrade = trade();

        engine.tradeCompletionListener(new TradeCompletionEvent(7L, activeTrade));

        verify(tradeWatchdog).unwatch(activeTrade);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertInstanceOf(NotificationRequest.class, event.getValue());
        assertEquals(7L, ((NotificationRequest) event.getValue()).userId());
        verify(tradeWatchdog).clearTrigger(activeTrade);
    }

    @Test
    void tradeCompletionListener_keepsWatchingWhileTheExitIsOnlyPartiallyFilled() throws Exception {
        when(orderRouterFactory.getRouter(BrokerType.RUPEEZY)).thenReturn(orderRouter);
        when(orderRouter.getOrderDetails(anyLong(), anyString()))
                .thenReturn(TradeOrderResponse.builder().orderId("X1").pendingQuantity(4).build());
        var activeTrade = trade();

        engine.tradeCompletionListener(new TradeCompletionEvent(7L, activeTrade));

        verify(tradeWatchdog, never()).unwatch(any(ActiveTrade.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        // The trigger must still be cleared, or this token can never fire again.
        verify(tradeWatchdog).clearTrigger(activeTrade);
    }

    @Test
    void tradeCompletionListener_clearsTheTriggerEvenWhenTheBrokerCallFails() {
        when(orderRouterFactory.getRouter(BrokerType.RUPEEZY)).thenThrow(new IllegalStateException("down"));
        var activeTrade = trade();

        try {
            engine.tradeCompletionListener(new TradeCompletionEvent(7L, activeTrade));
        } catch (Exception _) {
            // propagated by design; the finally block is what matters here
        }

        verify(tradeWatchdog).clearTrigger(activeTrade);
    }

    /** Registers an order for the strategy so the signal listener has something to act on. */
    private void registerOrder(String strategyName) {
        when(strategyOrderService.getTodayOrders()).thenReturn(List.of(order("s1", strategyName)));
        when(strategyService.getCachedStrategies())
                .thenReturn(Map.of(strategyName, StrategyDto.builder().name(strategyName).build()));
        engine.continuousTrade();
    }

    private ChartInkBacktestMarginDto signalAt(int minutesAgo) {
        return ChartInkBacktestMarginDto.builder()
                .marketTime(DateUtil.getCurrentDateTime().minusMinutes(minutesAgo))
                .margins(List.of(Margin.builder().symbol("TCS").token("11536")
                        .requiredMargin(new BigDecimal("4.5")).rupeezyMargin(new BigDecimal("4.0")).build()))
                .build();
    }
}
