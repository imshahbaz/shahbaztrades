package com.app.shahbaztrades.components.orderrouting;

import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.enums.BrokerType;

public interface OrderRoutingStrategy {

    BrokerType getBrokerType();

    TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request);

    TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request);

    void updateMTFStopLossOrder(Long userId, TradeOrderRequest request);

    void cancelOrder(Long userId, String orderId);

    void convertSLToMarket(Long userId, TradeOrderRequest request);

    TradeOrderResponse getOrderDetails(Long userId, String orderId);

    TradeOrderResponse placePreMarketOrder(Long userId, TradeOrderRequest request);
}