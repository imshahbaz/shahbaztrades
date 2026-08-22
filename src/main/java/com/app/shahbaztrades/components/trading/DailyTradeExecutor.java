package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.model.dto.order.MtfTickEvent;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.util.DateUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Manages an open daily MTF position as ticks arrive: arms a protective stop once the trade is far
 * enough ahead, and squares off when the trail is breached or the session is ending.
 * <p>
 * What to do is {@link TrailingStopPolicy}'s decision; this class only carries it out. The daily
 * counterpart to {@link ContinuousTradeExecutor}, which places and settles continuous trades.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTradeExecutor {

    private static final String BROKER_STATUS_EXECUTED = "EXECUTED";

    private final ApplicationEventPublisher eventPublisher;
    private final TradeWatchdog tradeWatchdog;
    private final OrderRouterFactory orderRouterFactory;
    private final TrailingStopPolicy trailingStopPolicy;
    private final TradeNotifier tradeNotifier;

    @EventListener
    @Async("taskExecutor")
    public void handleActiveMtfOrderEvent(MtfTickEvent event) {
        var trade = event.trade();
        try {
            if (isPositionClosed(trade.getOrder(), event.ltp(), event.peakPrice())) {
                log.info("Order squared off - stopping monitoring orderId {} symbol {}",
                        trade.getOrder().getId(), trade.getOrder().getSymbol());
                tradeWatchdog.unwatchMtfTrade(trade);
            }
        } finally {
            tradeWatchdog.clearMtfTrigger(trade);
        }
    }

    /** @return true when the position is gone and no longer worth watching. */
    private boolean isPositionClosed(Order order, double ltp, double peakPrice) {
        if (!order.hasEntryPrice()) {
            log.info("Skipped processing order {} doesn't have entry price for user {} symbol {}",
                    order.getId(), order.getUserId(), order.getSymbol());
            return true;
        }

        double buyPrice = order.getEntry().getAveragePrice().doubleValue();
        boolean hasNoExitOrder = !order.hasExitOrder();
        Double atrValue = order.getAtr() != null ? order.getAtr().getAtrValue() : null;

        var action = trailingStopPolicy.decide(ltp, buyPrice, peakPrice, atrValue,
                hasNoExitOrder, DateUtil.isPastClosingGrace());

        return switch (action) {
            case SQUARE_OFF -> squareOff(order, hasNoExitOrder);
            case PLACE_STOP_LOSS -> {
                placeStopLossOrder(order, buyPrice);
                yield false;
            }
            case NONE -> false;
        };
    }

    private boolean squareOff(Order order, boolean hasNoExitOrder) {
        log.info("Symbol: {}. Stock price dropped below the trail or the session is ending. Squaring off...",
                order.getSymbol());
        return hasNoExitOrder ? placeMarketSellOrder(order) : convertPendingExitToMarket(order);
    }

    /** @return true if the position is closed, false if it must stay watched for a retry. */
    private boolean placeMarketSellOrder(Order order) {
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .transactionType(Constants.TRANSACTION_TYPE_SELL).orderType(Constants.ORDER_TYPE_MARKET).build();
            orderRouter.placeMTFOrder(order.getUserId(), req);
            log.info("Successfully placed MTF sell order for user {} symbol {}", order.getUserId(), order.getSymbol());

            order.setOrderStatus(OrderStatus.COMPLETED);
            eventPublisher.publishEvent(order);
            tradeNotifier.marketSellPlaced(order.getUserId(), order.getQuantity(), order.getSymbol());
            return true;
        } catch (Exception e) {
            log.error("Failed to square off for {}", order.getSymbol(), e);
            return false;
        }
    }

    /** Turns a resting stop-loss into a market order so the exit actually fills. */
    private boolean convertPendingExitToMarket(Order order) {
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var orderDetails = orderRouter.getOrderDetails(order.getUserId(), order.getExit().getBrokerOrderId());
            if (orderDetails == null) {
                return false;
            }

            if (Objects.equals(orderDetails.getStatus(), Constants.ORDER_COMPLETE)
                    || Objects.equals(orderDetails.getStatus(), Constants.ORDER_REJECTED)
                    || Objects.equals(orderDetails.getStatus(), BROKER_STATUS_EXECUTED)) {
                log.info("Order has been completed/rejected for user {} symbol {} order status {}",
                        order.getUserId(), order.getSymbol(), orderDetails.getStatus());
                return true;
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
                tradeNotifier.marketSellPlaced(order.getUserId(), pendingQty, order.getSymbol());
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to convert order for user {} symbol {} error {}",
                    order.getUserId(), order.getSymbol(), e.getMessage());
            return false;
        }
    }

    private void placeStopLossOrder(Order order, double buyPrice) {
        var sl = trailingStopPolicy.stopLossPrice(buyPrice);
        try {
            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .price(sl).triggerPrice(sl).build();
            var res = orderRouter.placeMTFStopLossOrder(order.getUserId(), req);

            order.setExit(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId())
                    .averagePrice(BigDecimal.valueOf(sl)).build());
            order.setOrderStatus(OrderStatus.STOP_LOSS_ACTIVE);
            eventPublisher.publishEvent(order);
            tradeNotifier.stopLossPlaced(order.getUserId(), order.getQuantity(), order.getSymbol(), sl);
            log.info("Successfully placed MTF SL order for user {} symbol {}", order.getUserId(), order.getSymbol());
        } catch (Exception e) {
            log.error("Failed to place stop loss order for user {} symbol {} error {}",
                    order.getUserId(), order.getSymbol(), e.getMessage());
        }
    }
}
