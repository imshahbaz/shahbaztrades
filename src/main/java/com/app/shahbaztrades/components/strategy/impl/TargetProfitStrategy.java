package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.strategy.AbstractDailyTradingStrategy;
import com.app.shahbaztrades.components.yahoo.YahooClient;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

@Slf4j
@Component
public class TargetProfitStrategy extends AbstractDailyTradingStrategy {

    private static final String NAME = "TARGET PROFIT";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);

    protected TargetProfitStrategy(MongoTemplate mongoTemplate, ApplicationEventPublisher eventPublisher,
                                   OrderRouterFactory orderRouterFactory, YahooClient yahooClient, MarketFeed marketFeed) {
        super(mongoTemplate, eventPublisher, orderRouterFactory, yahooClient, marketFeed);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void startTrading(Order order) {
        if (!order.hasEntryPrice()) {
            log.info("Order {} doesn't have entry price for user {} symbol {}", order.getId(), order.getUserId(), order.getSymbol());
            return;
        }

        try {
            var entryPrice = order.getEntry().getAveragePrice();
            var targetPercentage = ONE_HUNDRED.add(order.getTargetPercentage()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            var target = entryPrice.multiply(targetPercentage);
            var targetPrice = HelperUtil.fixToTick(target.doubleValue());
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity()).price(targetPrice)
                    .transactionType(Constants.TRANSACTION_TYPE_SELL).orderType(Constants.ORDER_TYPE_LIMIT).build();
            var exitResp = orderRouter.placeMTFOrder(order.getUserId(), req);
            order.setExit(Order.ExecutionRecord.builder().brokerOrderId(exitResp.getOrderId()).orderStatus(exitResp.getStatus()).build());
            order.setOrderStatus(OrderStatus.COMPLETED);
            this.saveOrderProgress(order);
            this.publishNotification(NotificationRequest.builder()
                    .userId(order.getUserId())
                    .title(com.app.shahbaztrades.util.Constants.NOTIFICATION_TITLE_PLACED)
                    .body(String.format(com.app.shahbaztrades.util.Constants.NOTIFICATION_MESSAGE_SELL_LIMIT, order.getQuantity(), order.getSymbol(), targetPrice))
                    .data(Collections.emptyMap())
                    .build());
        } catch (Exception e) {
            log.error("Error placing exit order for {}", order.getId());
        }
    }

}
