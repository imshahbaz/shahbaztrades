package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Order records: create, amend, delete and query. Managing an order once the market opens belongs
 * to {@link com.app.shahbaztrades.service.MtfTradeEngine} and
 * {@link com.app.shahbaztrades.components.trading.DailyTradeExecutor}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final UserService userService;

    @Override
    public OrderDto getById(String id) {
        return this.getOrderById(id).toDto();
    }

    @Override
    public List<OrderDto> getOrdersByDate(String date) {
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception _) {
            throw new BadRequestException("Invalid date format");
        }

        return mongoTemplate.find(ordersOn(localDate), Order.class).stream().map(Order::toDto).toList();
    }

    @Override
    public List<OrderDto> getOrdersByUserId(long userId) {
        var today = DateUtil.getTodayDate();
        var startOfIstDay = today.atStartOfDay(DateUtil.IST_ZONE).toInstant();
        Query query = Query.query(Criteria.where(Order.Fields.userId).is(userId)
                .and(Order.Fields.date).gte(startOfIstDay));
        query.with(Sort.by(Order.Fields.date).descending());
        return mongoTemplate.find(query, Order.class).stream().map(Order::toDto).toList();
    }

    @Override
    public void createOrder(OrderDto orderDto) {
        var entity = validated(orderDto);
        try {
            mongoTemplate.insert(entity);
        } catch (DataIntegrityViolationException _) {
            throw new ResourceAlreadyExistsException("Order already exists");
        }
    }

    @Override
    public void updateOrder(OrderDto orderDto) {
        var entity = validated(orderDto);
        try {
            orderRepo.save(entity);
        } catch (DataIntegrityViolationException _) {
            throw new ResourceAlreadyExistsException("Order already exists for this user on this date");
        }
    }

    @Override
    public void deleteOrder(String id) {
        var order = this.getOrderById(id);
        OrderValidator.validateForDelete(order.getDate());
        orderRepo.deleteById(id);
    }

    @Override
    public List<Order> getTodayOrders() {
        return mongoTemplate.find(ordersOn(DateUtil.getTodayDate()), Order.class);
    }

    /**
     * Resolves the symbol's margin onto the order, then runs every rule that must hold before saving.
     */
    private Order validated(OrderDto orderDto) {
        var margin = marginService.getMarginCache().get(orderDto.getSymbol().toUpperCase());
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }

        var entity = orderDto.toEntity(margin);
        OrderValidator.validateOrder(entity);
        OrderValidator.validateBroker(
                userService.findByUserIdOrEmailOrMobile(entity.getUserId(), "", 0L), entity.getBroker());
        return entity;
    }

    /**
     * Orders are stored as instants, so an IST calendar day is a half-open instant range.
     */
    private Query ordersOn(LocalDate date) {
        Instant startOfIstDay = date.atStartOfDay(DateUtil.IST_ZONE).toInstant();
        Instant endOfIstDay = date.plusDays(1).atStartOfDay(DateUtil.IST_ZONE).toInstant();
        return Query.query(Criteria.where(Order.Fields.date).gte(startOfIstDay).lt(endOfIstDay));
    }

    private Order getOrderById(String orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }
}
