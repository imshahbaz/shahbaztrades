package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.util.TechnicalAnalysisUtil;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    enum StopLossAction {NONE, SQUARE_OFF, PLACE_STOP_LOSS}

    private static final String INITIATE_MTF = "Initiate MTF";
    private static final double PROFIT_ACTIVATION_MULTIPLIER = 1.004;
    private static final double STOP_LOSS_TRIGGER_MULTIPLIER = 1.006;
    private static final double PEAK_DROP_SQUARE_OFF_MULTIPLIER = 0.994;
    private static final double ATR_TRAILING_MULTIPLIER = 0.4;
    private final OrderRepo orderRepo;
    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final AngelOneService angelOneService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;
    private final YahooClient yahooClient;
    private final UserService userService;

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

    static OrderStatus mapEntryStatus(String brokerStatus) {
        if (StringUtils.isBlank(brokerStatus)) {
            return OrderStatus.PLACED;
        }

        return switch (brokerStatus.toUpperCase()) {
            case Constants.ORDER_COMPLETE, "EXECUTED" -> OrderStatus.BOUGHT;
            case Constants.ORDER_REJECTED -> OrderStatus.REJECTED;
            case Constants.ORDER_CANCELLED -> OrderStatus.FAILED;
            default -> OrderStatus.PLACED;
        };
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
        OrderValidator.validateForCreateAndUpdate(userService.findByUserIdOrEmailOrMobile(entity.getUserId(), "", 0L), entity.getBroker());
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
        OrderValidator.validateForCreateAndUpdate(userService.findByUserIdOrEmailOrMobile(entity.getUserId(), "", 0L), entity.getBroker());
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
        processTodayOrders(INITIATE_MTF, (order) -> {
            if (order.getOrderStatus() != OrderStatus.PENDING || (order.getEntry() != null && StringUtils.isNotBlank(order.getEntry().getBrokerOrderId()))) {
                log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
                return null;
            }

            try {
                var orderRouter = orderRouterFactory.getRouter(order.getBroker());
                var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                        .transactionType(Constants.TRANSACTION_TYPE_BUY).orderType(Constants.ORDER_TYPE_MARKET).build();
                var res = orderRouter.placeMTFOrder(order.getUserId(), req);
                order.setEntry(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).build());
                order.setOrderStatus(OrderStatus.PLACED);
                log.info("MTF order placed for user {} symbol {} at init", order.getUserId(), order.getSymbol());
                eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED,
                        String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_PLACED, order.getQuantity(), order.getSymbol()),
                        Collections.emptyMap()));
            } catch (Exception e) {
                order.setOrderStatus(OrderStatus.FAILED);
                log.error("Failed to place MTF order for user {} symbol {} error {} at init", order.getUserId(), order.getSymbol(), e.getMessage());
            }
            return null;
        });
    }

    @Override
    public void updateMtfOrderStatus() {
        processTodayOrders("Update MTF Status", ((order) -> {
            if (order.getOrderStatus() != OrderStatus.PLACED || !order.hasEntryOrder()) {
                log.info("Mtf order not found for userId {} symbol {} skipping status update", order.getUserId(), order.getSymbol());
                throw new NotFoundException("Mtf order not found");
            }

            try {
                var orderRouter = orderRouterFactory.getRouter(order.getBroker());
                var orderDetails = orderRouter.getOrderDetails(order.getUserId(), order.getEntry().getBrokerOrderId());
                order.getEntry().setOrderStatus(orderDetails.getStatus());
                order.getEntry().setAveragePrice(orderDetails.getAveragePrice());
                order.setOrderStatus(mapEntryStatus(orderDetails.getStatus()));
                log.info("MTF status updated for user {} symbol {} status {} at update", order.getUserId(), order.getSymbol(), order.getOrderStatus());

                if (order.getOrderStatus() == OrderStatus.BOUGHT && orderDetails.getAveragePrice() != null) {
                    eventPublisher.publishEvent(new NotificationRequest(order.getUserId(), com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_BUY,
                            String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_BUY, order.getQuantity(), order.getSymbol(), orderDetails.getAveragePrice().doubleValue()),
                            Collections.emptyMap()));
                }
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
            if (!order.hasEntryPrice()) {
                log.info("Watchdog skipped order {} doesn't have entry price for user {} symbol {}", order.getId(), order.getUserId(), order.getSymbol());
                return;
            }

            try {
                angelOneService.subscribe(order.getMargin().getToken(), ExchangeType.NSE.getValue());
            } catch (Exception _) {
                log.error("WS Subscription failed for {}", order.getSymbol());
                return;
            }

            tradeWatchdog.watchMtfTrade(ActiveMtfTrade.builder()
                    .order(order)
                    .peakPrice(order.getEntry().getAveragePrice().doubleValue())
                    .build());
        });
    }

    private void processTodayOrders(String type, Function<Order, Void> processor) {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for {}", type);
            return;
        }

        if (shouldUpdateAtr(type)) {
            updateAtr(orders);
        }

        processOrdersByUser(orders, processor);
    }

    private boolean shouldUpdateAtr(String type) {
        return INITIATE_MTF.equalsIgnoreCase(type);
    }

    private void updateAtr(List<Order> orders) {
        Map<String, TechnicalMetrics> metrics = new ConcurrentHashMap<>();
        orders.forEach(order -> updateAtr(order, metrics));
    }

    private void updateAtr(Order order, Map<String, TechnicalMetrics> metrics) {
        try {
            var res = metrics.computeIfAbsent(order.getSymbol(), this::getValidTechnicalMetrics);
            if (res != null) {
                order.setAtr(res);
            }
        } catch (Exception _) {
            log.error("Error updating ATR for {} orderId {}", order.getSymbol(), order.getId());
        }
    }

    private TechnicalMetrics getValidTechnicalMetrics(String symbol) {
        var data = yahooClient.getHistoricalData(symbol, YahooTimeRange.RANGE_1MO.getValue());
        if (CollectionUtils.isEmpty(data)) {
            return null;
        }

        var atr = TechnicalAnalysisUtil.getAtr(data);
        if (atr == null || !atr.isAtrValid()) {
            return null;
        }

        return atr;
    }

    private void processOrdersByUser(List<Order> orders, Function<Order, Void> processor) {
        Map<Long, List<Order>> userOrderMap = orders.stream()
                .collect(Collectors.groupingBy(Order::getUserId));

        userOrderMap.forEach((userId, userOrders) ->
                HelperUtil.EXECUTOR.execute(() -> processUserOrders(userId, userOrders, processor)));
    }

    private void processUserOrders(Long userId, List<Order> userOrders, Function<Order, Void> processor) {
        try {
            for (Order order : userOrders) {
                processor.apply(order);
                saveOrderProgress(order);
            }
        } catch (Exception e) {
            log.error("Error processing orders for user {}", userId, e);
        }
    }

    private void saveOrderProgress(Order order) {
        Query query = Query.query(Criteria.where(Order.Fields.id).is(order.getId()));
        Update update = new Update()
                .set(Order.Fields.entry, order.getEntry())
                .set(Order.Fields.exit, order.getExit())
                .set(Order.Fields.atr, order.getAtr())
                .set(Order.Fields.orderStatus, order.getOrderStatus());
        mongoTemplate.updateFirst(query, update, Order.class);
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
