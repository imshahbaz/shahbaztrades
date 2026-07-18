package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.DateUtil;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ZerodhaOrderRouter implements OrderRoutingStrategy {

    private final ZerodhaService zerodhaService;

    private static String getVariety() {
        return DateUtil.isMarketClosedForTrading() ? Constants.VARIETY_AMO : Constants.VARIETY_REGULAR;
    }

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.ZERODHA;
    }

    @Override
    public TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        OrderParams orderParams = new OrderParams();
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = request.getSymbol();
        orderParams.transactionType = request.getTransactionType();
        orderParams.quantity = request.getQuantity();
        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = request.getOrderType();
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.tag = "Shahbaz Trades";
        orderParams.price = request.getPrice();

        if (Constants.ORDER_TYPE_MARKET.equals(request.getOrderType())) {
            orderParams.marketProtection = -1;
        }

        OrderResponse res;
        try {
            res = kc.placeOrder(orderParams, getVariety());
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }

        if (res == null || res.orderId == null) {
            throw new IllegalStateException("Order placement failed: No Order ID returned");
        }

        return TradeOrderResponse.builder().orderId(res.orderId).build();
    }

    @Override
    public TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        OrderParams orderParams = new OrderParams();
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = request.getSymbol();
        orderParams.transactionType = Constants.TRANSACTION_TYPE_SELL;
        orderParams.quantity = request.getQuantity();
        orderParams.price = request.getPrice();
        orderParams.triggerPrice = request.getTriggerPrice();

        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = Constants.ORDER_TYPE_SL;
        orderParams.validity = Constants.VALIDITY_DAY;

        OrderResponse orderResponse;
        try {
            orderResponse = kc.placeOrder(orderParams, getVariety());
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }

        if (orderResponse == null || orderResponse.orderId == null) {
            throw new IllegalStateException("Order placement failed: No Order ID returned");
        }

        return TradeOrderResponse.builder().orderId(orderResponse.orderId).build();
    }

    @Override
    public void updateMTFStopLossOrder(Long userId, TradeOrderRequest request) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        OrderParams modParams = new OrderParams();
        modParams.price = request.getPrice();
        modParams.triggerPrice = request.getTriggerPrice();
        try {
            kc.modifyOrder(request.getOrderId(), modParams, getVariety());
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void cancelOrder(Long userId, String orderId) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        try {
            kc.cancelOrder(orderId, getVariety(), null);
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void convertSLToMarket(Long userId, TradeOrderRequest request) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        OrderParams params = new OrderParams();
        params.orderType = Constants.ORDER_TYPE_MARKET;
        params.quantity = request.getQuantity();
        params.price = null;
        params.triggerPrice = null;
        params.marketProtection = -1;
        try {
            kc.modifyOrder(request.getOrderId(), params, getVariety());
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public TradeOrderResponse getOrderDetails(Long userId, String orderId) throws Exception {
        var kc = zerodhaService.getKiteClient(userId);
        List<Order> history;
        try {
            history = kc.getOrderHistory(orderId);
        } catch (KiteException e) {
            throw new IllegalStateException(e);
        }

        if (CollectionUtils.isEmpty(history)) {
            throw new NotFoundException("No history found for order id " + orderId);
        }

        var detail = history.getLast();
        return TradeOrderResponse.builder()
                .orderId(detail.orderId)
                .status(detail.status)
                .averagePrice(NumberUtils.isCreatable(detail.averagePrice) ? new BigDecimal(detail.averagePrice) : BigDecimal.ZERO)
                .pendingQuantity(NumberUtils.isCreatable(detail.pendingQuantity) ? Integer.parseInt(detail.pendingQuantity) : 0)
                .build();
    }
}
