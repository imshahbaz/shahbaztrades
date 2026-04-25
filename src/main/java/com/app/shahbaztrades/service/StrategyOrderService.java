package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;

import java.util.List;

public interface StrategyOrderService {

    List<StrategyOrderDto> getAllOrdersAdmin(String strategyName);

    StrategyOrderDto getOrderById(String orderId);

    StrategyOrderDto createOrder(StrategyOrderDto request);

    StrategyOrderDto updateOrder(StrategyOrderDto request);

    void deleteOrder(String id);

    List<StrategyOrderDto> getOrdersByUserId(long userId);
}
