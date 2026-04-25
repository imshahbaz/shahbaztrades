package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Order;

import java.util.List;

public interface OrderService {

    OrderDto getById(String id);

    List<OrderDto> getOrdersByDate(String date);

    List<OrderDto> getOrdersByUserId(long userId);

    void createOrder(OrderDto orderDto);

    void updateOrder(OrderDto orderDto);

    void deleteOrder(String id);

    List<Order> getTodayOrders();

    void initiateMtfOrders();

    void updateMtfOrderStatus();

    void startTrading();
}
