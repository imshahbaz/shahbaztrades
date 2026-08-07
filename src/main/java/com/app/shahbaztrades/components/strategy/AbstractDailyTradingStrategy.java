package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.model.enums.YahooTimeRange;
import com.app.shahbaztrades.service.AngelOneService;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.util.TechnicalAnalysisUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Map;

@Slf4j
public abstract class AbstractDailyTradingStrategy implements DailyTradingStrategy {

    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;
    protected final OrderRouterFactory orderRouterFactory;
    private final YahooClient yahooClient;
    protected final AngelOneService angelOneService;

    protected AbstractDailyTradingStrategy(MongoTemplate mongoTemplate, ApplicationEventPublisher eventPublisher,
                                           OrderRouterFactory orderRouterFactory, YahooClient yahooClient, AngelOneService angelOneService) {
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
        this.orderRouterFactory = orderRouterFactory;
        this.yahooClient = yahooClient;
        this.angelOneService = angelOneService;
    }

    @Override
    public void initialiseTrade(Order order, Map<String, TechnicalMetrics> metrics) {
        updateAtr(order, metrics);
        if (order.getOrderStatus() != OrderStatus.PENDING || (order.getEntry() != null && StringUtils.isNotBlank(order.getEntry().getBrokerOrderId()))) {
            log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
            return;
        }

        try {
            var ltp = angelOneService.getLTP(order.getMargin().getToken());
            if (ltp <= 0) {
                try {
                    angelOneService.subscribe(order.getMargin().getToken(), ExchangeType.NSE.getValue());
                    HelperUtil.pollWait(1000);
                    ltp = angelOneService.getLTP(order.getMargin().getToken());
                } catch (Exception _) {
                    log.error("WS Subscription failed for {}", order.getSymbol());
                }
            }

            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .transactionType(Constants.TRANSACTION_TYPE_BUY).price(ltp <= 0 ? null : ltp * 1.02).build();
            var res = orderRouter.placePreMarketOrder(order.getUserId(), req);
            order.setEntry(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).build());
            order.setOrderStatus(OrderStatus.PLACED);
            log.info("MTF order placed for user {} symbol {} at init", order.getUserId(), order.getSymbol());
            this.publishNotification(NotificationRequest.builder()
                    .userId(order.getUserId())
                    .title(com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED)
                    .body(String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_PLACED, order.getQuantity(), order.getSymbol()))
                    .data(Collections.emptyMap())
                    .build());
        } catch (Exception e) {
            order.setOrderStatus(OrderStatus.FAILED);
            log.error("Failed to place MTF order for user {} symbol {} error {} at init", order.getUserId(), order.getSymbol(), e.getMessage());
        }

        this.saveOrderProgress(order);
    }

    @Override
    public void updateTradeStatus(Order order) {
        if (order.getOrderStatus() != OrderStatus.PLACED || !order.hasEntryOrder()) {
            log.info("Mtf order not found for userId {} symbol {} skipping status update", order.getUserId(), order.getSymbol());
            return;
        }

        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var orderDetails = orderRouter.getOrderDetails(order.getUserId(), order.getEntry().getBrokerOrderId());
            order.getEntry().setOrderStatus(orderDetails.getStatus());
            order.getEntry().setAveragePrice(orderDetails.getAveragePrice());
            order.setOrderStatus(DailyTradingStrategy.mapEntryStatus(orderDetails.getStatus()));
            log.info("MTF status updated for user {} symbol {} status {} at update", order.getUserId(), order.getSymbol(), order.getOrderStatus());

            if (order.getOrderStatus() == OrderStatus.BOUGHT && orderDetails.getAveragePrice() != null) {
                this.publishNotification(NotificationRequest.builder()
                        .userId(order.getUserId())
                        .title(com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_BUY)
                        .body(String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_BUY, order.getQuantity(), order.getSymbol(), orderDetails.getAveragePrice().doubleValue()))
                        .data(Collections.emptyMap())
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to update MTF status for user {} symbol {} error {} at update", order.getUserId(), order.getSymbol(), e.getMessage());
            return;
        }

        this.saveOrderProgress(order);
    }

    protected void saveOrderProgress(Order order) {
        Query query = Query.query(Criteria.where(Order.Fields.id).is(order.getId()));
        Update update = new Update()
                .set(Order.Fields.entry, order.getEntry())
                .set(Order.Fields.exit, order.getExit())
                .set(Order.Fields.atr, order.getAtr())
                .set(Order.Fields.orderStatus, order.getOrderStatus());

        try {
            mongoTemplate.updateFirst(query, update, Order.class);
        } catch (Exception e) {
            log.error("Error updating order status {} updates {}", order.getId(), update);
        }
    }

    protected void publishNotification(NotificationRequest request) {
        if (request == null) return;
        eventPublisher.publishEvent(request);
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

}
