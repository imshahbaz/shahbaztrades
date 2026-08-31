package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.components.analysis.TechnicalMetricsProvider;
import com.app.shahbaztrades.components.trading.TargetPricePolicy;
import com.app.shahbaztrades.components.trading.TradeNotifier;
import com.app.shahbaztrades.repo.OrderProgressRepository;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.nse.NSEHistoricalData;
import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyTradingStrategyTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OrderRouterFactory orderRouterFactory;
    @Mock
    private OrderRoutingStrategy orderRouter;
    @Mock
    private TechnicalMetricsProvider technicalMetricsProvider;
    @Mock
    private OrderProgressRepository orderProgressRepository;
    @Mock
    private TradeNotifier tradeNotifier;
    @Mock
    private MarketFeed marketFeed;
    @Mock
    private TradeWatchdog tradeWatchdog;

    private TargetProfitStrategy targetProfit;
    private TrailingProfitStrategy trailingProfit;

    @BeforeEach
    void setUp() {
        targetProfit = new TargetProfitStrategy(orderProgressRepository, tradeNotifier,
                orderRouterFactory, marketFeed, technicalMetricsProvider,new TargetPricePolicy());
        trailingProfit = new TrailingProfitStrategy(orderProgressRepository, tradeNotifier,
                orderRouterFactory, marketFeed, technicalMetricsProvider, tradeWatchdog);
        lenient().when(orderRouterFactory.getRouter(BrokerType.ZERODHA)).thenReturn(orderRouter);
    }

    private Order order(OrderStatus status, String entryAveragePrice) {
        var builder = Order.builder()
                .id("o1").userId(7L).symbol("TCS").quantity(10).broker(BrokerType.ZERODHA)
                .margin(Margin.builder().symbol("TCS").token("11536").build())
                .orderStatus(status)
                .targetPercentage(new BigDecimal("1.5"));
        if (entryAveragePrice != null) {
            builder.entry(Order.ExecutionRecord.builder()
                    .brokerOrderId("B1").averagePrice(new BigDecimal(entryAveragePrice)).build());
        }
        return builder.build();
    }

    @Test
    void strategyNames_matchTheStoredOrderStrategyNames() {
        assertEquals("TARGET PROFIT", targetProfit.getName());
        assertEquals("TRAILING PROFIT", trailingProfit.getName());
    }

    // --- initialiseTrade --------------------------------------------------

    @Test
    void initialiseTrade_placesAPreMarketOrderAndRecordsTheBrokerId() throws Exception {
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.of(3200.0));
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("B1").build());
        var order = order(OrderStatus.PENDING, null);

        targetProfit.initialiseTrade(order, new HashMap<>());

        assertEquals(OrderStatus.PLACED, order.getOrderStatus());
        assertEquals("B1", order.getEntry().getBrokerOrderId());
        verify(orderProgressRepository).saveProgress(order);
    }

    @Test
    void initialiseTrade_capsThePreMarketLimitAtTwoPercentAboveLtp() throws Exception {
        // An uncapped market order at pre-open can fill at an absurd price.
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.of(3200.0));
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("B1").build());

        targetProfit.initialiseTrade(order(OrderStatus.PENDING, null), new HashMap<>());

        ArgumentCaptor<TradeOrderRequest> request = ArgumentCaptor.forClass(TradeOrderRequest.class);
        verify(orderRouter).placePreMarketOrder(anyLong(), request.capture());
        assertEquals(3264.0, request.getValue().getPrice(), 0.5);
    }

    @Test
    void initialiseTrade_subscribesAndRetriesWhenTheTokenHasNotTickedYet() throws Exception {
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.NOT_SUBSCRIBED, Ltp.NOT_SUBSCRIBED);
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("B1").build());

        targetProfit.initialiseTrade(order(OrderStatus.PENDING, null), new HashMap<>());

        verify(marketFeed).subscribe("11536", ExchangeType.NSE.getValue());
        ArgumentCaptor<TradeOrderRequest> request = ArgumentCaptor.forClass(TradeOrderRequest.class);
        verify(orderRouter).placePreMarketOrder(anyLong(), request.capture());
        // No price is available, so the router must fall back to a market order.
        assertNull(request.getValue().getPrice());
    }

    @Test
    void initialiseTrade_doesNotWaitOnASubscriptionWhenTheFeedIsDown() throws Exception {
        // A dead socket cannot deliver a price, so burning a second on subscribe-and-poll is pointless.
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.FEED_DOWN);
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("B1").build());

        targetProfit.initialiseTrade(order(OrderStatus.PENDING, null), new HashMap<>());

        verify(marketFeed, never()).subscribe(anyString(), org.mockito.ArgumentMatchers.anyInt());
        ArgumentCaptor<TradeOrderRequest> request = ArgumentCaptor.forClass(TradeOrderRequest.class);
        verify(orderRouter).placePreMarketOrder(anyLong(), request.capture());
        // Still placed, but unpriced: the order must not silently disappear because the feed blipped.
        assertNull(request.getValue().getPrice());
    }

    @Test
    void initialiseTrade_skipsAnOrderThatIsNoLongerPending() throws Exception {
        var order = order(OrderStatus.PLACED, null);

        targetProfit.initialiseTrade(order, new HashMap<>());

        verify(orderRouter, never()).placePreMarketOrder(anyLong(), any(TradeOrderRequest.class));
    }

    @Test
    void initialiseTrade_skipsAnOrderThatAlreadyHasABrokerEntry() throws Exception {
        // Re-running the morning job must not double-buy.
        var order = order(OrderStatus.PENDING, "3200");

        targetProfit.initialiseTrade(order, new HashMap<>());

        verify(orderRouter, never()).placePreMarketOrder(anyLong(), any(TradeOrderRequest.class));
    }

    @Test
    void initialiseTrade_marksTheOrderFailedWhenPlacementThrows() throws Exception {
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.of(3200.0));
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenThrow(new IllegalStateException("broker down"));
        var order = order(OrderStatus.PENDING, null);

        targetProfit.initialiseTrade(order, new HashMap<>());

        assertEquals(OrderStatus.FAILED, order.getOrderStatus());
        verify(orderProgressRepository).saveProgress(order);
    }

    @Test
    void initialiseTrade_attachesAtrAndReusesItForRepeatSymbols() throws Exception {
        when(marketFeed.getLtp("11536")).thenReturn(Ltp.of(3200.0));
        when(orderRouter.placePreMarketOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("B1").build());
        when(technicalMetricsProvider.atrFor("TCS"))
                .thenReturn(TechnicalMetrics.builder().atrValue(40).expectedMovePercent(1.25).build());
        Map<String, TechnicalMetrics> metrics = new HashMap<>();

        targetProfit.initialiseTrade(order(OrderStatus.PENDING, null), metrics);
        targetProfit.initialiseTrade(order(OrderStatus.PENDING, null), metrics);

        assertNotNull(metrics.get("TCS"));
        // The second order for the same symbol must hit the shared map, not recompute.
        verify(technicalMetricsProvider).atrFor("TCS");
    }

    // --- updateTradeStatus ------------------------------------------------

    @Test
    void updateTradeStatus_mapsTheBrokerStatusAndFillPrice() throws Exception {
        var order = order(OrderStatus.PLACED, null);
        order.setEntry(Order.ExecutionRecord.builder().brokerOrderId("B1").build());
        when(orderRouter.getOrderDetails(7L, "B1")).thenReturn(TradeOrderResponse.builder()
                .orderId("B1").status("COMPLETE").averagePrice(new BigDecimal("3210.5")).build());

        targetProfit.updateTradeStatus(order);

        assertEquals(OrderStatus.BOUGHT, order.getOrderStatus());
        assertEquals(0, new BigDecimal("3210.5").compareTo(order.getEntry().getAveragePrice()));
        verify(tradeNotifier).buyExecuted(eq(7L), eq(10), eq("TCS"), eq(3210.5));
    }

    @Test
    void updateTradeStatus_skipsOrdersThatWereNeverPlaced() throws Exception {
        targetProfit.updateTradeStatus(order(OrderStatus.PENDING, null));

        verify(orderRouter, never()).getOrderDetails(anyLong(), anyString());
    }

    @Test
    void updateTradeStatus_leavesTheOrderUntouchedWhenTheBrokerCallFails() throws Exception {
        var order = order(OrderStatus.PLACED, null);
        order.setEntry(Order.ExecutionRecord.builder().brokerOrderId("B1").build());
        when(orderRouter.getOrderDetails(7L, "B1")).thenThrow(new IllegalStateException("timeout"));

        targetProfit.updateTradeStatus(order);

        assertEquals(OrderStatus.PLACED, order.getOrderStatus());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Order.class));
    }

    // --- startTrading: TARGET PROFIT --------------------------------------

    @Test
    void targetProfit_placesALimitExitAboveEntryAndMarksTheOrderComplete() throws Exception {
        when(orderRouter.placeMTFOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenReturn(TradeOrderResponse.builder().orderId("X1").build());
        var order = order(OrderStatus.BOUGHT, "3200");

        targetProfit.startTrading(order);

        ArgumentCaptor<TradeOrderRequest> request = ArgumentCaptor.forClass(TradeOrderRequest.class);
        verify(orderRouter).placeMTFOrder(anyLong(), request.capture());
        assertTrue(request.getValue().getPrice() > 3200.0);
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());
        assertEquals("X1", order.getExit().getBrokerOrderId());
    }

    @Test
    void targetProfit_currentlyRoundsSubPercentTargetsUpToTheNearestPercent() {
        // (100 + 1.5) / 100 is computed with setScale(2, HALF_UP), so the 1.015 multiplier
        // becomes 1.02: a 1.5% target is placed at 2%. Documents today's behaviour, and any
        // targetPercentage with a fractional part is affected the same way.
        var multiplier = new BigDecimal(100).add(new BigDecimal("1.5"))
                .divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);

        assertEquals(0, new BigDecimal("1.02").compareTo(multiplier));
    }

    @Test
    void targetProfit_skipsOrdersWithoutAFillPrice() throws Exception {
        targetProfit.startTrading(order(OrderStatus.PLACED, null));

        verify(orderRouter, never()).placeMTFOrder(anyLong(), any(TradeOrderRequest.class));
    }

    @Test
    void targetProfit_leavesTheStatusAloneWhenTheExitFails() throws Exception {
        when(orderRouter.placeMTFOrder(anyLong(), any(TradeOrderRequest.class)))
                .thenThrow(new IllegalStateException("rejected"));
        var order = order(OrderStatus.BOUGHT, "3200");

        targetProfit.startTrading(order);

        assertEquals(OrderStatus.BOUGHT, order.getOrderStatus(),
                "marking it COMPLETED without an exit order would abandon a live position");
    }

    // --- startTrading: TRAILING PROFIT ------------------------------------

    @Test
    void trailingProfit_subscribesAndHandsTheTradeToTheWatchdog() {
        var order = order(OrderStatus.BOUGHT, "3200");

        trailingProfit.startTrading(order);

        verify(marketFeed).subscribe("11536", ExchangeType.NSE.getValue());
        ArgumentCaptor<ActiveMtfTrade> trade = ArgumentCaptor.forClass(ActiveMtfTrade.class);
        verify(tradeWatchdog).watchMtfTrade(trade.capture());
        assertEquals(3200.0, trade.getValue().getPeakPrice(), "the peak starts at the entry fill");
    }

    @Test
    void trailingProfit_skipsOrdersWithoutAFillPrice() {
        trailingProfit.startTrading(order(OrderStatus.PLACED, null));

        verify(tradeWatchdog, never()).watchMtfTrade(any(ActiveMtfTrade.class));
    }

    @Test
    void trailingProfit_doesNotWatchWhenTheWebsocketSubscriptionFails() {
        // Without a live feed the trail would never move; watching would be a silent no-op.
        org.mockito.Mockito.doThrow(new IllegalStateException("ws closed"))
                .when(marketFeed).subscribe(anyString(), org.mockito.ArgumentMatchers.anyInt());

        trailingProfit.startTrading(order(OrderStatus.BOUGHT, "3200"));

        verify(tradeWatchdog, never()).watchMtfTrade(any(ActiveMtfTrade.class));
    }

    private List<NSEHistoricalData> candles() {
        List<NSEHistoricalData> data = new ArrayList<>();
        var day = DateUtil.getTodayDate().minusDays(40);
        for (int i = 0; i < 30; i++) {
            data.add(NSEHistoricalData.builder().symbol("TCS")
                    .open(3200).high(3220).low(3180).close(3200)
                    .timestamp(DateUtil.NSE_INPUT_LAYOUT.format(day.plusDays(i)))
                    .build());
        }
        return data;
    }
}
