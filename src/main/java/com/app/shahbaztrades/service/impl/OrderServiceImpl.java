package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.validator.OrderValidator;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final AngelOneService angelOneService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;

    @Override
    public OrderDto getById(String id) {
        return this.getOrderById(id).toDto();
    }

    @Override
    public List<OrderDto> getOrdersByDate(String date) {
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }
        Instant startOfIstDay = localDate.atStartOfDay(DateUtil.IST_ZONE).toInstant();
        Instant endOfIstDay = localDate.plusDays(1).atStartOfDay(DateUtil.IST_ZONE).toInstant();

        Query query = Query.query(
                Criteria.where(Order.Fields.date)
                        .gte(startOfIstDay)
                        .lt(endOfIstDay)
        );

        return mongoTemplate.find(query, Order.class).stream().map(Order::toDto).toList();
    }

    @Override
    public List<OrderDto> getOrdersByUserId(long userId) {
        Query query = Query.query(Criteria.where(Order.Fields.userId).is(userId));
        return mongoTemplate.find(query, Order.class).stream().map(Order::toDto).toList();
    }

    @Override
    public void createOrder(OrderDto orderDto) {
        var margin = marginService.getMarginCache().get(orderDto.getSymbol().toUpperCase());
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }

        var entity = orderDto.toEntity(margin);
        try {
            mongoTemplate.insert(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException("Order already exists");
        }
    }

    @Override
    public void updateOrder(OrderDto orderDto) {
        var margin = marginService.getMarginCache().get(orderDto.getSymbol().toUpperCase());
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }
        var entity = orderDto.toEntity(margin);
        try {
            orderRepo.save(entity);
        } catch (DataIntegrityViolationException e) {
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
        var today = DateUtil.getTodayDate();
        var startOfIstDay = today.atStartOfDay(DateUtil.IST_ZONE).toInstant();
        var endOfIstDay = today.plusDays(1).atStartOfDay(DateUtil.IST_ZONE).toInstant();
        Query query = Query.query(Criteria.where(Order.Fields.date)
                .gte(startOfIstDay)
                .lt(endOfIstDay));
        return mongoTemplate.find(query, Order.class);
    }

    @Override
    @Async("taskExecutor")
    public void initiateMtfOrders() {
        processTodayOrders("Initiate MTF", (order) -> {
            if (order.getEntry() != null && StringUtils.isNotBlank(order.getEntry().getBrokerOrderId())) {
                log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
                return null;
            }

            try {
                var orderRouter = orderRouterFactory.getRouter(order.getBroker());
                var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                        .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_MARKET).build();
                var res = orderRouter.placeMTFOrder(order.getUserId(), req);
                order.setEntry(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).build());
                log.info("MTF order placed for user {} symbol {} at init", order.getUserId(), order.getSymbol());
                eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED,
                        String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_PLACED, order.getQuantity(), order.getSymbol()),
                        Collections.emptyMap()));
            } catch (Exception e) {
                log.error("Failed to place MTF order for user {} symbol {} error {} at init", order.getUserId(), order.getSymbol(), e.getMessage());
            }
            return null;
        });
    }

    @Override
    public void updateMtfOrderStatus() {
        processTodayOrders("Update MTF Status", ((order) -> {
            if (order.getEntry() == null || StringUtils.isEmpty(order.getEntry().getBrokerOrderId())) {
                log.info("Mtf order not found for userId {} symbol {} skipping status update", order.getUserId(), order.getSymbol());
                throw new NotFoundException("Mtf order not found");
            }

            try {
                var orderRouter = orderRouterFactory.getRouter(order.getBroker());
                var orderDetails = orderRouter.getOrderDetails(order.getUserId(), order.getEntry().getBrokerOrderId());
                order.getEntry().setOrderStatus(orderDetails.getStatus());
                order.getEntry().setAveragePrice(orderDetails.getAveragePrice());
                log.info("MTF status updated for user {} symbol {} at update", order.getUserId(), order.getSymbol());
                eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_BUY,
                        String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_BUY, order.getQuantity(), order.getSymbol(), orderDetails.getAveragePrice()),
                        Collections.emptyMap()));
            } catch (Exception e) {
                log.error("Failed to update MTF status for user {} symbol {} error {} at update", order.getUserId(), order.getSymbol(), e.getMessage());
                throw new BadRequestException("Invalid MTF order or kite exception " + e.getMessage());
            }

            return null;
        }));
    }

    @Override
    public void startTrading() {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today");
            return;
        }

        orders.forEach(order -> {
            if (order.getEntry() == null || order.getEntry().getAveragePrice() <= 0) return;
            try {
                angelOneService.subscribe(order.getMargin().getToken(), ExchangeType.NSE.getValue());
            } catch (Exception e) {
                log.error("WS Subscription failed for {}", order.getSymbol());
                return;
            }

            tradeWatchdog.watchMtfTrade(ActiveMtfTrade.builder()
                    .order(order)
                    .peakPrice(order.getEntry().getAveragePrice())
                    .build());
        });
    }

    private void processTodayOrders(String type, Function<Order, Void> processor) {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for {}", type);
            return;
        }

        Map<Long, List<Order>> userOrderMap = orders.stream()
                .collect(Collectors.groupingBy(Order::getUserId));

        userOrderMap.forEach((userId, userOrders) -> HelperUtil.EXECUTOR.execute(() -> {
            try {
                for (Order order : userOrders) {
                    processor.apply(order);
                    saveOrderProgress(order);
                }
            } catch (Exception e) {
                log.error("Error processing orders for user {}", userId, e);
            }
        }));
    }

    private void saveOrderProgress(Order order) {
        Query query = Query.query(Criteria.where(Order.Fields.id).is(order.getId()));
        Update update = new Update()
                .set(Order.Fields.entry, order.getEntry())
                .set(Order.Fields.exit, order.getExit());
        mongoTemplate.updateFirst(query, update, Order.class);
    }

    private short processOrder(Order order, double ltp, double peakPrice) {
        if (order.getEntry() == null || order.getEntry().getAveragePrice() == 0) {
            return -1;
        }

        return addStopLoss(order, ltp, order.getEntry().getAveragePrice(), peakPrice);
    }

    private short addStopLoss(Order order, double ltp, double buyPrice, double peakPrice) {
        boolean reachedProfitThreshold = ltp > buyPrice * 1.004;
        boolean droppedFromPeak = ltp <= peakPrice * 0.994;
        boolean marketClosing = DateUtil.isPastClosingGrace();

        Order.ExecutionRecord exitRecord = order.getExit();
        boolean hasNoExitOrder = (exitRecord == null || StringUtils.isEmpty(exitRecord.getBrokerOrderId()));

        if (reachedProfitThreshold && (droppedFromPeak || marketClosing)) {
            return handleSquareOff(order, hasNoExitOrder);
        }

        if (hasNoExitOrder && ltp >= buyPrice * 1.006) {
            return placeStopLossOrder(order, buyPrice);
        }

        return 0;
    }

    private short handleSquareOff(Order order, boolean hasNoExitOrder) {
        log.info("Symbol: {}. Stock price dropped more than 0.6% or Market is closing (3:25 PM). Squaring off...", order.getSymbol());
        if (hasNoExitOrder) {
            return placeMarketSellOrder(order);
        } else {
            return convertPendingExitToMarket(order);
        }
    }

    private short placeMarketSellOrder(Order order) {
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .transactionType(Constants.TRANSACTION_TYPE_SELL).orderType(Constants.ORDER_TYPE_MARKET).build();
            orderRouter.placeMTFOrder(order.getUserId(), req);
            log.info("Successfully placed MTF sell order for user {} symbol {}", order.getUserId(), order.getSymbol());
            eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED,
                    String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_SELL_MARKET, order.getQuantity(), order.getSymbol()),
                    Collections.emptyMap()));
            return -1;
        } catch (Exception e) {
            log.error("Failed to square off for {}", order.getSymbol(), e);
            return 0;
        }
    }

    private short convertPendingExitToMarket(Order order) {
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var orderDetails = orderRouter.getOrderDetails(order.getUserId(), order.getExit().getBrokerOrderId());
            if (orderDetails == null) return 0;

            if (Objects.equals(orderDetails.getStatus(), Constants.ORDER_COMPLETE) || Objects.equals(orderDetails.getStatus(), Constants.ORDER_REJECTED) ||
                    Objects.equals(orderDetails.getStatus(), "EXECUTED")) {
                log.info("Order has been completed/rejected for user {} symbol {} order status {}", order.getUserId(), order.getSymbol(), orderDetails.getStatus());
                return -1;
            }

            var pendingQty = orderDetails.getPendingQuantity();
            if (pendingQty > 0) {
                var req = TradeOrderRequest.builder()
                        .orderId(order.getExit().getBrokerOrderId())
                        .symbol(order.getSymbol())
                        .quantity(pendingQty)
                        .transactionType(Constants.TRANSACTION_TYPE_SELL)
                        .orderType(Constants.ORDER_TYPE_MARKET)
                        .build();

                orderRouter.convertSLToMarket(order.getUserId(), req);
                log.info("MTF SL order converted to market for user {} symbol {}", order.getUserId(), order.getSymbol());
                eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED,
                        String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_SELL_MARKET, pendingQty, order.getSymbol()),
                        Collections.emptyMap()));
            }
            return -1;
        } catch (Exception e) {
            log.error("Failed to convert order for user {} symbol {} error {}", order.getUserId(), order.getSymbol(), e.getMessage());
            return 0;
        }
    }

    private short placeStopLossOrder(Order order, double buyPrice) {
        var sl = HelperUtil.fixToTick(buyPrice * 1.004);
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .price(sl).triggerPrice(sl).build();
            var res = orderRouter.placeMTFStopLossOrder(order.getUserId(), req);
            order.setExit(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).averagePrice((float) sl).build());
            eventPublisher.publishEvent(order);
            eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED,
                    String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_SELL_SL, order.getQuantity(), order.getSymbol(), sl),
                    Collections.emptyMap()));
            log.info("Successfully placed MTF SL order for user {} symbol {}", order.getUserId(), order.getSymbol());
            return 1;
        } catch (Exception e) {
            log.error("Failed to place stop loss order for user {} symbol {} error {}", order.getUserId(), order.getSymbol(), e.getMessage());
            return 0;
        }
    }

    @EventListener
    @Async("taskExecutor")
    public void handleActiveMtfOrderEvent(ActiveMtfTrade event) {
        var order = event.getOrder();
        var res = processOrder(order, event.getLtp(), event.getPeakPrice());
        if (res < 0) {
            log.info("Order squared off - stopping monitoring orderId {} symbol {}", order.getId(), order.getSymbol());
            tradeWatchdog.unwatchMtfTrade(event);
        }
    }

    private Order getOrderById(String orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

}
