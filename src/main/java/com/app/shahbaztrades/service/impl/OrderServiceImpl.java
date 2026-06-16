package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.zerodha.ZerodhaOrderClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.OrderService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final MongoTemplate mongoTemplate;
    private final MarginService marginService;
    private final ZerodhaService zerodhaService;
    private final AngelOneService angelOneService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;

    @Override
    public OrderDto getById(String id) {
        var order = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return order.toDto();
    }

    @Override
    public List<OrderDto> getOrdersByDate(String date) {
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }
        Query query = Query.query(Criteria.where(Order.Fields.date).gte(localDate.atStartOfDay())
                .and(Order.Fields.date).lte(localDate.plusDays(1).atStartOfDay()));

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
        processTodayOrders("Initiate MTF", (kc, order) -> {
            if (order.getEntry() != null && StringUtils.isNotBlank(order.getEntry().getBrokerOrderId())) {
                log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
                return null;
            }

            try {
                var res = ZerodhaOrderClient.placeMTFOrder(kc, order.getSymbol(), order.getQuantity(), 0,
                        Constants.TRANSACTION_TYPE_BUY, Constants.ORDER_TYPE_MARKET);
                order.setEntry(Order.ExecutionRecord.builder().brokerOrderId(res.orderId).build());
            } catch (KiteException | Exception e) {
                log.error("Failed to place MTF order", e);
            }
            return null;
        });
    }

    @Override
    public void updateMtfOrderStatus() {
        processTodayOrders("Update MTF Status", ((kc, order) -> {
            if (order.getEntry() == null || StringUtils.isEmpty(order.getEntry().getBrokerOrderId())) {
                log.info("Mtf order not found for userId {} symbol {} skipping status update", order.getUserId(), order.getSymbol());
                throw new NotFoundException("Mtf order not found");
            }

            try {
                var orderDetails = ZerodhaOrderClient.getOrderDetails(kc, order.getEntry().getBrokerOrderId());
                order.getEntry().setOrderStatus(orderDetails.status);
                order.getEntry().setAveragePrice(StringUtils.isNumeric(orderDetails.averagePrice) ? Float.parseFloat(orderDetails.averagePrice) : 0);
            } catch (Exception | KiteException e) {
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

    private void processTodayOrders(String type, BiFunction<KiteConnect, Order, Void> processor) {
        var orders = getTodayOrders();
        if (CollectionUtils.isEmpty(orders)) {
            log.info("No Orders found for today for {}", type);
            return;
        }

        Map<Long, List<Order>> userOrderMap = orders.stream()
                .collect(Collectors.groupingBy(Order::getUserId));

        userOrderMap.forEach((userId, userOrders) -> HelperUtil.EXECUTOR.execute(() -> {
            try {
                KiteConnect kc = zerodhaService.getKiteClient(userId);
                for (Order order : userOrders) {
                    processor.apply(kc, order);
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

    private short processOrder(Order order, double ltp, float peakPrice) {
        if (order.getEntry() == null || order.getEntry().getAveragePrice() == 0) {
            return -1;
        }

        KiteConnect kc;
        try {
            kc = zerodhaService.getKiteClient(order.getUserId());
        } catch (Exception e) {
            log.error("Failed to get kite connection for {}", order.getUserId(), e);
            return -1;
        }

        return addStopLoss(order, ltp, order.getEntry().getAveragePrice(), kc, peakPrice);
    }

    private short addStopLoss(Order order, double ltp, float buyPrice, KiteConnect kc, float peakPrice) {
        boolean reachedProfitThreshold = ltp > buyPrice * 1.004;
        boolean droppedFromPeak = ltp <= peakPrice * 0.994;
        boolean marketClosing = DateUtil.isPastClosingGrace();

        Order.ExecutionRecord exitRecord = order.getExit();
        boolean hasNoExitOrder = (exitRecord == null || StringUtils.isEmpty(exitRecord.getBrokerOrderId()));

        if (reachedProfitThreshold && (droppedFromPeak || marketClosing)) {
            log.info("Symbol: {}. Stock price dropped more than 0.6% or Market is closing (3:25 PM). Squaring off...",
                    order.getSymbol());

            if (hasNoExitOrder) {
                try {
                    ZerodhaOrderClient.placeMTFOrder(kc, order.getSymbol(), order.getQuantity(), 0, Constants.TRANSACTION_TYPE_SELL, Constants.ORDER_TYPE_MARKET);
                } catch (Exception | KiteException e) {
                    log.error("Failed to square off for {}", order.getSymbol(), e);
                    return 0;
                }
            } else {
                try {
                    var orderDetails = ZerodhaOrderClient.getOrderDetails(kc, order.getExit().getBrokerOrderId());
                    if (orderDetails == null) {
                        return 0;
                    }

                    if (Objects.equals(orderDetails.status, Constants.ORDER_COMPLETE) || Objects.equals(orderDetails.status, Constants.ORDER_REJECTED)) {
                        return -1;
                    }

                    var pendingQty = StringUtils.isNumeric(orderDetails.pendingQuantity) ? Integer.parseInt(orderDetails.pendingQuantity) : 0;
                    if (pendingQty > 0) {
                        ZerodhaOrderClient.convertSLToMarket(kc, order.getExit().getBrokerOrderId(), pendingQty);
                    }
                } catch (Exception | KiteException e) {
                    log.error("Failed to convert order for {}", order.getSymbol(), e);
                    return 0;
                }

            }

            return -1;
        }

        if (hasNoExitOrder && ltp >= buyPrice * 1.006) {
            var sl = HelperUtil.fixToTick(buyPrice * 1.004);
            try {
                var orderId = ZerodhaOrderClient.placeMTFStopLossOrder(kc, order.getSymbol(), order.getQuantity(), sl, sl);
                order.setExit(Order.ExecutionRecord.builder().brokerOrderId(orderId).averagePrice((float) sl).build());
                eventPublisher.publishEvent(order);
                return 1;
            } catch (Exception | KiteException e) {
                log.error("Failed to place stop loss order for {}", order.getSymbol(), e);
                return 0;
            }
        }

        return 0;
    }

    @EventListener
    @Async("taskExecutor")
    public void handleActiveMtfOrderEvent(ActiveMtfTrade event) {
        var ltp = event.getLtp();
        var order = event.getOrder();
        if (event.getLtp() > 0 && ltp != event.getPrevLtp()) {
            var res = processOrder(order, ltp, event.getPeakPrice());
            if (res < 0) {
                log.info("Order squared off - stopping monitoring orderId {} symbol {}", order.getId(), order.getSymbol());
                tradeWatchdog.unwatchMtfTrade(event);
                return;
            }

            event.setPrevLtp(ltp);
            if (ltp > event.getPeakPrice()) {
                event.setPeakPrice((float) ltp);
            }
        }
    }

}
