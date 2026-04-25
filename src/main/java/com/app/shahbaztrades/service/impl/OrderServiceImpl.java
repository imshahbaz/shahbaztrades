package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.helper.AsyncHelper;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.AngelOneWebSocketService;
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
    private final AngelOneWebSocketService angelOneWebSocketService;
    private final AsyncHelper asyncHelper;

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
        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(orderDto.getDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }

        var margin = marginService.getMarginCache().get(orderDto.getSymbol().toUpperCase());
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }

        var entity = Order.builder()
                .userId(orderDto.getUserId())
                .symbol(margin.getSymbol())
                .quantity(orderDto.getQuantity())
                .date(orderDate.atStartOfDay())
                .margin(margin).build();

        try {
            mongoTemplate.insert(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ResourceAlreadyExistsException("Order already exists");
        }
    }

    @Override
    public void updateOrder(OrderDto orderDto) {
        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(orderDto.getDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format");
        }

        var margin = marginService.getMarginCache().get(orderDto.getSymbol().toUpperCase());
        if (margin == null) {
            throw new NotFoundException("Margin not found");
        }

        Query query = new Query(Criteria.where(Order.Fields.id).is(orderDto.getId()));
        Update update = new Update();
        update.set(Order.Fields.date, orderDate.atStartOfDay());
        update.set(Order.Fields.quantity, orderDto.getQuantity());
        update.set(Order.Fields.margin, margin.getMargin());
        update.set(Order.Fields.symbol, orderDto.getSymbol().toUpperCase());
        var res = mongoTemplate.updateFirst(query, update, Order.class);
        if (res.getModifiedCount() < 1) {
            throw new BadRequestException("Order not found");
        }
    }

    @Override
    public void deleteOrder(String id) {
        orderRepo.deleteById(id);
    }

    @Override
    public List<Order> getTodayOrders() {
        var today = DateUtil.getTodayDate();
        Query query = Query.query(Criteria.where(Order.Fields.date).gte(today.atStartOfDay())
                .and(Order.Fields.date).lt(today.plusDays(1).atStartOfDay()));
        return mongoTemplate.find(query, Order.class);
    }

    @Async
    @Override
    public void initiateMtfOrders() {
        processTodayOrders("Initiate MTF", (kc, order) -> {
            if (order.getBuyOrder() != null && StringUtils.isNotBlank(order.getBuyOrder().getOrderId())) {
                log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
                return null;
            }

            try {
                var res = zerodhaService.placeMTFOrder(kc, order.getSymbol(), order.getQuantity(), 0,
                        Constants.TRANSACTION_TYPE_BUY, Constants.ORDER_TYPE_MARKET);
                order.setBuyOrder(Order.OrderInfo.builder().orderId(res.orderId).build());
            } catch (KiteException | Exception e) {
                log.error("Failed to place MTF order: {}", e.getMessage());
            }
            return null;
        });
    }

    @Override
    public void updateMtfOrderStatus() {
        processTodayOrders("Update MTF Status", ((kc, order) -> {
            if (order.getBuyOrder() == null || StringUtils.isEmpty(order.getBuyOrder().getOrderId())) {
                log.info("Mtf order not found for userId {} symbol {} skipping status update", order.getUserId(), order.getSymbol());
                throw new NotFoundException("Mtf order not found");
            }

            try {
                var orderDetails = zerodhaService.getOrderDetails(kc, order.getBuyOrder().getOrderId());
                order.getBuyOrder().setOrderStatus(orderDetails.status);
                order.getBuyOrder().setAveragePrice(StringUtils.isNumeric(orderDetails.averagePrice) ? Float.parseFloat(orderDetails.averagePrice) : 0);
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
            if (order.getBuyOrder() == null || order.getBuyOrder().getAveragePrice() <= 0) return;
            try {
                angelOneWebSocketService.subscribe(order.getMargin().getToken(), ExchangeType.NSE.getValue());
            } catch (Exception e) {
                log.error("WS Subscription failed for {}", order.getSymbol());
                return;
            }

            HelperUtil.EXECUTOR.execute(() -> {
                double prevLtp = 0;
                var peakPrice = order.getBuyOrder().getAveragePrice();
                String token = order.getMargin().getToken();

                log.info("Started LTP monitoring for {} (Entry: {})", order.getSymbol(), peakPrice);

                while (!Thread.currentThread().isInterrupted()) {
                    var ltp = angelOneWebSocketService.getLTP(token);
                    if (ltp == -2) {
                        log.error("LTP feed lost for {}", order.getSymbol());
                        break;
                    }

                    if (ltp > 0 && ltp != prevLtp) {
                        var res = processOrder(order, ltp, peakPrice);
                        if (res < 0) {
                            log.info("Order squared off - stopping monitoring orderId {} symbol {}", order.getId(), order.getSymbol());
                            break;
                        }
                        prevLtp = ltp;
                        if (ltp > peakPrice) {
                            peakPrice = (float) ltp;
                        }
                    }

                    if (!HelperUtil.pollWait(200)) {
                        break;
                    }
                }
            });
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
                log.error("Error processing orders for user {}: {}", userId, e.getMessage());
            }
        }));
    }

    private void saveOrderProgress(Order order) {
        Query query = Query.query(Criteria.where(Order.Fields.id).is(order.getId()));
        Update update = new Update()
                .set(Order.Fields.buyOrder, order.getBuyOrder())
                .set(Order.Fields.stopLossOrder, order.getStopLossOrder());
        mongoTemplate.updateFirst(query, update, Order.class);
    }

    private short processOrder(Order order, double ltp, float peakPrice) {
        if (order.getBuyOrder() == null || order.getBuyOrder().getAveragePrice() == 0) {
            return -1;
        }

        KiteConnect kc;
        try {
            kc = zerodhaService.getKiteClient(order.getUserId());
        } catch (Exception e) {
            log.error("Failed to get kite connection for {} error {}", order.getUserId(), e.getMessage());
            return -1;
        }

        return addStopLoss(order, ltp, order.getBuyOrder().getAveragePrice(), kc, peakPrice);
    }

    private short addStopLoss(Order order, double ltp, float buyPrice, KiteConnect kc, float peakPrice) {
        if (ltp > buyPrice * 1.004 && (ltp <= peakPrice * 0.994 || DateUtil.isPastClosingGrace())) {
            log.info("Symbol: {}. Stock price dropped more than 0.6% or Market is closing (3:25 PM). Squaring off...",
                    order.getSymbol());

            if (order.getStopLossOrder() == null || StringUtils.isEmpty(order.getStopLossOrder().getOrderId())) {
                try {
                    zerodhaService.placeMTFOrder(kc, order.getSymbol(), order.getQuantity(), 0, Constants.TRANSACTION_TYPE_SELL, Constants.ORDER_TYPE_MARKET);
                } catch (Exception | KiteException e) {
                    log.error("Failed to square off for {} error {}", order.getSymbol(), e.getMessage());
                    return 0;
                }
            } else {
                try {
                    var orderDetails = zerodhaService.getOrderDetails(kc, order.getStopLossOrder().getOrderId());
                    if (orderDetails == null) {
                        return 0;
                    }

                    if (Objects.equals(orderDetails.status, Constants.ORDER_COMPLETE) || Objects.equals(orderDetails.status, Constants.ORDER_REJECTED)) {
                        return -1;
                    }

                    var pendingQty = StringUtils.isNumeric(orderDetails.pendingQuantity) ? Integer.parseInt(orderDetails.pendingQuantity) : 0;
                    if (pendingQty > 0) {
                        zerodhaService.convertSLToMarket(kc, order.getStopLossOrder().getOrderId(), pendingQty, 0);
                    }
                } catch (Exception | KiteException e) {
                    log.error("Failed to convert order for {} error {}", order.getSymbol(), e.getMessage());
                    return 0;
                }

            }

            return -1;
        }

        if ((order.getStopLossOrder() == null || StringUtils.isEmpty(order.getStopLossOrder().getOrderId())) && ltp >= buyPrice * 1.006) {
            var sl = HelperUtil.fixToTick(buyPrice * 1.004);
            try {
                var orderId = zerodhaService.placeMTFStopLossOrder(kc, order.getSymbol(), order.getQuantity(), sl, sl);
                order.setStopLossOrder(Order.OrderInfo.builder().orderId(orderId).averagePrice((float) sl).build());
                asyncHelper.post(order);
                return 1;
            } catch (Exception | KiteException e) {
                log.error("Failed to place stop loss order for {} error {}", order.getSymbol(), e.getMessage());
                return 0;
            }
        }

        return 0;
    }

}
