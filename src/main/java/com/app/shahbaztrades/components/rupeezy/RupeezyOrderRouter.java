package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.components.orderrouting.OrderRoutingStrategy;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderHistory;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderResponseDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.model.enums.RupeezyOrderType;
import com.app.shahbaztrades.service.RupeezyService;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RupeezyOrderRouter implements OrderRoutingStrategy {

    private final RupeezyClient rupeezyClient;
    private final RupeezyService rupeezyService;

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.RUPEEZY;
    }

    @Override
    public TradeOrderResponse placeMTFOrder(Long userId, TradeOrderRequest request) {
        var cache = getTokenCache(userId);
        var req = RupeezyOrderDto.builder()
                .ticker(ExchangeType.NSE.name() + ":" + request.getSymbol())
                .transactionType(request.getTransactionType())
                .product(Constants.PRODUCT_MTF)
                .quantity(request.getQuantity()).price(request.getPrice() == null ? 0 : request.getPrice())
                .triggerPrice(request.getTriggerPrice() == null ? 0 : request.getTriggerPrice())
                .variety(getOrderType(request))
                .build();

        return placeOrder(req, cache);
    }

    @Override
    public TradeOrderResponse placeMTFStopLossOrder(Long userId, TradeOrderRequest request) {
        var cache = getTokenCache(userId);
        var req = RupeezyOrderDto.builder()
                .ticker(ExchangeType.NSE.name() + ":" + request.getSymbol())
                .transactionType(Constants.TRANSACTION_TYPE_SELL)
                .product(Constants.PRODUCT_MTF)
                .quantity(request.getQuantity())
                .price(request.getPrice() == null ? 0 : request.getPrice())
                .triggerPrice(request.getTriggerPrice() == null ? 0 : request.getTriggerPrice())
                .variety(RupeezyOrderType.SL.getType())
                .build();

        return placeOrder(req, cache);
    }

    @Override
    public void updateMTFStopLossOrder(Long userId, TradeOrderRequest request) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public void cancelOrder(Long userId, String orderId) {
        var cache = getTokenCache(userId);
        RupeezyOrderResponseDto res;
        try {
            res = rupeezyClient.cancelOrder(orderId, cache.getApiSecret(), RupeezyClient.BEARER + cache.getAccessToken());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (res == null || !res.isSuccess()) {
            throw new IllegalStateException("Can't cancellation failed");
        }
    }

    @Override
    public void convertSLToMarket(Long userId, TradeOrderRequest request) {
        cancelOrder(userId, request.getOrderId());
        placeMTFOrder(userId, request);
    }

    @Override
    public TradeOrderResponse getOrderDetails(Long userId, String orderId) {
        var cache = getTokenCache(userId);
        RupeezyOrderHistory res;
        try {
            res = rupeezyClient.getOrder(cache.getApiSecret(), RupeezyClient.BEARER + cache.getAccessToken());
        } catch (Exception e) {
            throw new NotFoundException("Can't get order details");
        }

        if (res == null || !res.isSuccess()) {
            throw new NotFoundException("Can't get order details");
        }

        var orderData = res.getOrder(orderId);
        if (orderData == null) {
            throw new NotFoundException("Order not found");
        }

        return TradeOrderResponse.builder()
                .orderId(orderData.getOrderId())
                .status(orderData.getStatus())
                .averagePrice(orderData.getAveragePrice())
                .pendingQuantity(orderData.getPendingQuantity())
                .build();
    }

    private RupeezyTokenCache getTokenCache(Long userId) {
        var cache = rupeezyService.getTokenCache(userId);
        if (cache == null) {
            throw new NotFoundException("Access token not found");
        }
        return cache;
    }

    private String getOrderType(TradeOrderRequest request) {
        var type = request.getOrderType();
        switch (type) {
            case Constants.ORDER_TYPE_MARKET -> {
                return RupeezyOrderType.REGULAR_MARKET.getType();
            }

            case Constants.ORDER_TYPE_LIMIT -> {
                return RupeezyOrderType.REGULAR_LIMIT.getType();
            }

            default -> throw new BadRequestException("Unknown order type: " + type);
        }
    }

    private TradeOrderResponse placeOrder(RupeezyOrderDto req, RupeezyTokenCache cache) {
        RupeezyOrderResponseDto res;
        try {
            res = rupeezyClient.placeOrder(req, cache.getApiSecret(), RupeezyClient.BEARER + cache.getAccessToken());
            if (res == null || res.getOrderId() == null) {
                throw new IllegalStateException("Order placement failed: No Order ID returned");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Order placement failed: " + e.getMessage(), e);
        }

        return TradeOrderResponse.builder().orderId(res.getOrderId()).build();
    }

}
