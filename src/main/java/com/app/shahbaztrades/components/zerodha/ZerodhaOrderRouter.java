package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.DateUtil;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZerodhaOrderRouter implements OrderRoutingStrategy {

    private final ZerodhaService zerodhaService;

    private static final String ORDER_TAG = "Shahbaz Trades";
    private static final int NO_MARKET_PROTECTION = -1;

    @FunctionalInterface
    private interface KiteCall<T> {
        T execute(KiteConnect kiteConnect) throws KiteException, IOException, JSONException;
    }

    private static String getVariety() {
        return DateUtil.isMarketClosedForTrading() ? Constants.VARIETY_AMO : Constants.VARIETY_REGULAR;
    }

    private <T> T withKiteClient(Long userId, String context, KiteCall<T> call) {
        try {
            return call.execute(zerodhaService.getKiteClient(userId));
        } catch (KiteException | IOException | JSONException e) {
            log.error("{} | userId {} | error {}", context, userId, e.getMessage());
            throw new IllegalStateException(context, e);
        }
    }

    private TradeOrderResponse requireOrderId(Long userId, String symbol, OrderResponse res) {
        if (res == null || res.orderId == null) {
            log.error("Order placement returned no order id for userId {} symbol {}", userId, symbol);
            throw new IllegalStateException("Order placement failed: No Order ID returned");
        }
        return TradeOrderResponse.builder().orderId(res.orderId).build();
    }

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.ZERODHA;
    }

    @Override
    public TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request) {
        OrderParams orderParams = new OrderParams();
        orderParams.exchange = Constants.EXCHANGE_NSE;
        orderParams.tradingsymbol = request.getSymbol();
        orderParams.transactionType = request.getTransactionType();
        orderParams.quantity = request.getQuantity();
        orderParams.product = Constants.PRODUCT_MTF;
        orderParams.orderType = request.getOrderType();
        orderParams.validity = Constants.VALIDITY_DAY;
        orderParams.tag = ORDER_TAG;
        orderParams.price = request.getPrice();

        if (Constants.ORDER_TYPE_MARKET.equals(request.getOrderType())) {
            orderParams.marketProtection = NO_MARKET_PROTECTION;
        }

        OrderResponse res = withKiteClient(userId,
                "Failed to place MTF order for " + request.getSymbol() + " quantity " + request.getQuantity() + " orderType " + request.getOrderType(),
                kc -> kc.placeOrder(orderParams, getVariety()));

        return requireOrderId(userId, request.getSymbol(), res);
    }

    @Override
    public TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request) {
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
        orderParams.tag = ORDER_TAG;

        OrderResponse orderResponse = withKiteClient(userId,
                "Failed to place MTF stop-loss order for " + request.getSymbol() + " quantity " + request.getQuantity() + " triggerPrice " + request.getTriggerPrice(),
                kc -> kc.placeOrder(orderParams, getVariety()));

        return requireOrderId(userId, request.getSymbol(), orderResponse);
    }

    @Override
    public void convertSLToMarket(Long userId, TradeOrderRequest request) {
        OrderParams params = new OrderParams();
        params.orderType = Constants.ORDER_TYPE_MARKET;
        params.quantity = request.getQuantity();
        params.price = null;
        params.triggerPrice = null;
        params.marketProtection = NO_MARKET_PROTECTION;

        withKiteClient(userId,
                "Failed to convert stop-loss order to market for order " + request.getOrderId() + " symbol " + request.getSymbol(),
                kc -> kc.modifyOrder(request.getOrderId(), params, getVariety()));
    }

    @Override
    public TradeOrderResponse getOrderDetails(Long userId, String orderId) {
        List<Order> history = withKiteClient(userId,
                "Failed to fetch order details for order " + orderId,
                kc -> kc.getOrderHistory(orderId));

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

    @Override
    public TradeOrderResponse placePreMarketOrder(Long userId, TradeOrderRequest request) {
        request.setPrice(null);
        request.setOrderType(Constants.ORDER_TYPE_MARKET);
        return this.placeMTFOrder(userId, request);
    }
}
