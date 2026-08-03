package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.MtfTickEvent;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.validator.OrderValidator;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String INITIATE_MTF = "Initiate MTF";
    private static final double PROFIT_ACTIVATION_MULTIPLIER = 1.004;
    private static final double STOP_LOSS_TRIGGER_MULTIPLIER = 1.006;
    private static final double PEAK_DROP_SQUARE_OFF_MULTIPLIER = 0.994;
    private static final double ATR_TRAILING_MULTIPLIER = 0.4;
    private final OrderRepo orderRepo;
    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;
    private final UserService userService;
    private final DailyTradingStrategyRegistry dailyTradingStrategyRegistry;

    static StopLossAction decideStopLossAction(double ltp, double buyPrice, double peakPrice,
                                               Double atrValue, boolean hasNoExitOrder, boolean marketClosing) {
        boolean reachedProfitThreshold = ltp > buyPrice * PROFIT_ACTIVATION_MULTIPLIER;

        boolean squareOff;
        if (atrValue != null) {
            double stopLossFloor = peakPrice - (atrValue * ATR_TRAILING_MULTIPLIER);
            squareOff = ltp <= stopLossFloor;
        } else {
            squareOff = ltp <= peakPrice * PEAK_DROP_SQUARE_OFF_MULTIPLIER;
        }

        if (reachedProfitThreshold && (squareOff || marketClosing)) {
            return StopLossAction.SQUARE_OFF;
        }

        if (hasNoExitOrder && ltp >= buyPrice * STOP_LOSS_TRIGGER_MULTIPLIER) {
            return StopLossAction.PLACE_STOP_LOSS;
        }

        return StopLossAction.NONE;
    }

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
        OrderValidator.validateOrder(entity);
        OrderValidator.validateBroker(userService.findByUserIdOrEmailOrMobile(entity.getUserId(), "", 0L), entity.getBroker());
        try {
            mongoTemplate.insert(entity);
        } catch (DataIntegrityViolationException _) {
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
        OrderValidator.validateOrder(entity);
        OrderValidator.validateBroker(userService.findByUserIdOrEmailOrMobile(entity.getUserId(), "", 0L), entity.getBroker());
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
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for {}", INITIATE_MTF);
            return;
        }

        var metrics = new ConcurrentHashMap<String, TechnicalMetrics>();
        for (var order : orders) {
            try {
                var strategy = dailyTradingStrategyRegistry.getStrategy(order.getStrategyName());
                strategy.initialiseTrade(order, metrics);
            } catch (Exception e) {
                log.error("Error processing mtf order {}", order.getId(), e);
            }
        }
    }

    @Override
    public void updateMtfOrderStatus() {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for Update MTF Status");
            return;
        }

        for (var order : orders) {
            try {
                var strategy = dailyTradingStrategyRegistry.getStrategy(order.getStrategyName());
                strategy.updateTradeStatus(order);
            } catch (Exception e) {
                log.error("Error updating mtf order status {}", order.getId(), e);
            }
        }
    }

    @Override
    public void startTrading() {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today");
            return;
        }

        for (var order : orders) {
            try {
                var strategy = dailyTradingStrategyRegistry.getStrategy(order.getStrategyName());
                strategy.startTrading(order);
            } catch (Exception e) {
                log.error("Error in mtf order {}", order.getId(), e);
            }
        }
    }

    private short processOrder(Order order, double ltp, double peakPrice) {
        if (!order.hasEntryPrice()) {
            log.info("Skipped processing order {} doesn't have entry price for user {} symbol {}", order.getId(), order.getUserId(), order.getSymbol());
            return -1;
        }

        return addStopLoss(order, ltp, order.getEntry().getAveragePrice().doubleValue(), peakPrice);
    }

    private short addStopLoss(Order order, double ltp, double buyPrice, double peakPrice) {
        boolean marketClosing = DateUtil.isPastClosingGrace();
        boolean hasNoExitOrder = !order.hasExitOrder();

        Double atrValue = order.getAtr() != null ? order.getAtr().getAtrValue() : null;

        StopLossAction action = decideStopLossAction(ltp, buyPrice, peakPrice, atrValue, hasNoExitOrder, marketClosing);
        if (action == StopLossAction.SQUARE_OFF) {
            return handleSquareOff(order, hasNoExitOrder);
        }

        if (action == StopLossAction.PLACE_STOP_LOSS) {
            return placeStopLossOrder(order, buyPrice);
        }

        return 0;
    }

    private short handleSquareOff(Order order, boolean hasNoExitOrder) {
        log.info("Symbol: {}. Stock price dropped more than 0.6% or Market is closing (3:14 PM). Squaring off...", order.getSymbol());
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
            order.setOrderStatus(OrderStatus.COMPLETED);
            eventPublisher.publishEvent(order);
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
                order.setOrderStatus(OrderStatus.COMPLETED);
                eventPublisher.publishEvent(order);
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
        var sl = HelperUtil.fixToTick(buyPrice * PROFIT_ACTIVATION_MULTIPLIER);
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .price(sl).triggerPrice(sl).build();
            var res = orderRouter.placeMTFStopLossOrder(order.getUserId(), req);
            order.setExit(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).averagePrice(BigDecimal.valueOf(sl)).build());
            order.setOrderStatus(OrderStatus.STOP_LOSS_ACTIVE);
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
    public void handleActiveMtfOrderEvent(MtfTickEvent event) {
        var trade = event.trade();
        try {
            var order = trade.getOrder();
            var res = processOrder(order, event.ltp(), event.peakPrice());
            if (res < 0) {
                log.info("Order squared off - stopping monitoring orderId {} symbol {}", order.getId(), order.getSymbol());
                tradeWatchdog.unwatchMtfTrade(trade);
            }
        } finally {
            tradeWatchdog.clearMtfTrigger(trade);
        }
    }

    private Order getOrderById(String orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    enum StopLossAction {NONE, SQUARE_OFF, PLACE_STOP_LOSS}

}
