package com.app.shahbaztrades.components.orderrouting;

import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.enums.BrokerType;

public interface OrderRoutingStrategy {

    BrokerType getBrokerType();

    TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request) throws Exception;

    TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request) throws Exception;

    void updateMTFStopLossOrder(Long userId, TradeOrderRequest request) throws Exception;

    void cancelOrder(Long userId, String orderId) throws Exception;

    void convertSLToMarket(Long userId, String orderId, int quantity) throws Exception;

    TradeOrderResponse getOrderDetails(Long userId, String orderId) throws Exception;
}