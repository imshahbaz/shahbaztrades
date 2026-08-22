package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZerodhaOrderRouterTest {

    @Mock
    private ZerodhaClientFactory zerodhaClientFactory;
    @Mock
    private KiteConnect kiteConnect;

    private ZerodhaOrderRouter router;

    @BeforeEach
    void setUp() throws Throwable {
        router = new ZerodhaOrderRouter(zerodhaClientFactory);
        lenient().when(zerodhaClientFactory.forUser(7L)).thenReturn(kiteConnect);
    }

    private OrderResponse response(String orderId) {
        OrderResponse response = new OrderResponse();
        response.orderId = orderId;
        return response;
    }

    private Order historyRow(String orderId, String status, String avgPrice, String pendingQty) {
        Order order = new Order();
        order.orderId = orderId;
        order.status = status;
        order.averagePrice = avgPrice;
        order.pendingQuantity = pendingQty;
        return order;
    }

    private TradeOrderRequest request() {
        return TradeOrderRequest.builder()
                .symbol("TCS").quantity(10).price(3300.0).triggerPrice(3300.0)
                .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_LIMIT)
                .orderId("O1")
                .build();
    }

    @Test
    void getBrokerType_isZerodha() throws Throwable {
        assertEquals(BrokerType.ZERODHA, router.getBrokerType());
    }

    // --- placement --------------------------------------------------------

    @Test
    void placeMTFOrder_sendsAnMtfNseOrderAndReturnsTheBrokerId() throws Throwable {
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString())).thenReturn(response("O1"));

        assertEquals("O1", router.placeMTFOrder(7L, request()).getOrderId());

        ArgumentCaptor<OrderParams> params = ArgumentCaptor.forClass(OrderParams.class);
        verify(kiteConnect).placeOrder(params.capture(), anyString());
        assertEquals(Constants.EXCHANGE_NSE, params.getValue().exchange);
        assertEquals(Constants.PRODUCT_MTF, params.getValue().product);
        assertEquals("TCS", params.getValue().tradingsymbol);
        assertEquals(Constants.VALIDITY_DAY, params.getValue().validity);
    }

    @Test
    void placeMTFOrder_disablesMarketProtectionOnMarketOrders() throws Throwable {
        // Kite's default 3% market protection silently rejects fast-moving fills.
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString())).thenReturn(response("O1"));
        var req = request();
        req.setOrderType(Constants.ORDER_TYPE_MARKET);

        router.placeMTFOrder(7L, req);

        ArgumentCaptor<OrderParams> params = ArgumentCaptor.forClass(OrderParams.class);
        verify(kiteConnect).placeOrder(params.capture(), anyString());
        assertEquals(-1, params.getValue().marketProtection);
    }

    @Test
    void placeMTFOrder_failsWhenTheBrokerReturnsNoOrderId() throws Throwable {
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString())).thenReturn(response(null));

        assertThrows(IllegalStateException.class, () -> router.placeMTFOrder(7L, request()));
    }

    @Test
    void placeMTFOrder_wrapsKiteExceptionsAsIllegalState() throws Throwable {
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString()))
                .thenThrow(new KiteException("rejected") {
                });

        assertThrows(IllegalStateException.class, () -> router.placeMTFOrder(7L, request()));
    }

    @Test
    void placeMTFStopLossOrder_alwaysSellsWithAnSlOrderType() throws Throwable {
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString())).thenReturn(response("X1"));

        assertEquals("X1", router.placeMTFStopLossOrder(7L, request()).getOrderId());

        ArgumentCaptor<OrderParams> params = ArgumentCaptor.forClass(OrderParams.class);
        verify(kiteConnect).placeOrder(params.capture(), anyString());
        // The request said BUY; a stop-loss exit must never inherit that.
        assertEquals(Constants.TRANSACTION_TYPE_SELL, params.getValue().transactionType);
        assertEquals(Constants.ORDER_TYPE_SL, params.getValue().orderType);
        assertEquals(3300.0, params.getValue().triggerPrice);
    }

    @Test
    void placePreMarketOrder_stripsThePriceAndSendsAMarketOrder() throws Throwable {
        when(kiteConnect.placeOrder(any(OrderParams.class), anyString())).thenReturn(response("O1"));
        var req = request();

        router.placePreMarketOrder(7L, req);

        ArgumentCaptor<OrderParams> params = ArgumentCaptor.forClass(OrderParams.class);
        verify(kiteConnect).placeOrder(params.capture(), anyString());
        assertNull(params.getValue().price);
        assertEquals(Constants.ORDER_TYPE_MARKET, params.getValue().orderType);
    }

    // --- modification -----------------------------------------------------

    @Test
    void convertSLToMarket_modifiesTheOrderToAnUnprotectedMarketOrder() throws Throwable {
        router.convertSLToMarket(7L, request());

        ArgumentCaptor<OrderParams> params = ArgumentCaptor.forClass(OrderParams.class);
        verify(kiteConnect).modifyOrder(org.mockito.ArgumentMatchers.eq("O1"), params.capture(), anyString());
        assertEquals(Constants.ORDER_TYPE_MARKET, params.getValue().orderType);
        // Leaving a stale trigger price behind would make Kite reject the modification.
        assertNull(params.getValue().price);
        assertNull(params.getValue().triggerPrice);
        assertEquals(-1, params.getValue().marketProtection);
    }



    // --- reads ------------------------------------------------------------

    @Test
    void getOrderDetails_usesTheLatestHistoryRow() throws Throwable {
        // Kite returns the full state machine; only the last row reflects the current status.
        when(kiteConnect.getOrderHistory("O1")).thenReturn(new ArrayList<>(List.of(
                historyRow("O1", "OPEN", "0", "10"),
                historyRow("O1", "COMPLETE", "3250.75", "0"))));

        var response = router.getOrderDetails(7L, "O1");

        assertEquals("COMPLETE", response.getStatus());
        assertEquals(0, new BigDecimal("3250.75").compareTo(response.getAveragePrice()));
        assertEquals(0, response.getPendingQuantity());
    }

    @Test
    void getOrderDetails_defaultsUnparseableNumbersInsteadOfThrowing() throws Throwable {
        // Kite sends "" for these fields on freshly-queued orders.
        when(kiteConnect.getOrderHistory("O1")).thenReturn(new ArrayList<>(List.of(
                historyRow("O1", "PUT ORDER REQ RECEIVED", "", ""))));

        var response = router.getOrderDetails(7L, "O1");

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getAveragePrice()));
        assertEquals(0, response.getPendingQuantity());
    }

    @Test
    void getOrderDetails_throwsWhenTheOrderHasNoHistory() throws Throwable {
        when(kiteConnect.getOrderHistory("O1")).thenReturn(new ArrayList<>());

        assertThrows(NotFoundException.class, () -> router.getOrderDetails(7L, "O1"));
    }

    @Test
    void getOrderDetails_wrapsKiteExceptionsAsIllegalState() throws Throwable {
        when(kiteConnect.getOrderHistory("O1")).thenThrow(new KiteException("boom") {
        });

        assertThrows(IllegalStateException.class, () -> router.getOrderDetails(7L, "O1"));
    }
}
