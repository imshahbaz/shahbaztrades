package com.app.shahbaztrades.components.strategy;

import com.app.shahbaztrades.components.analysis.TechnicalMetricsProvider;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.trading.TradeNotifier;
import com.app.shahbaztrades.model.dto.analysis.TechnicalMetrics;
import com.app.shahbaztrades.model.dto.order.TradeOrderRequest;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.repo.OrderProgressRepository;
import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.service.MarketFeed;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Slf4j
public abstract class AbstractDailyTradingStrategy implements DailyTradingStrategy {

    /** Pre-open orders fill against an empty book, so the entry is capped just above the last price. */
    private static final double PRE_MARKET_LIMIT_MULTIPLIER = 1.02;
    private static final long SUBSCRIBE_SETTLE_MILLIS = 1000;

    protected final OrderProgressRepository orderProgressRepository;
    protected final TradeNotifier tradeNotifier;
    protected final OrderRouterFactory orderRouterFactory;
    protected final MarketFeed marketFeed;
    private final TechnicalMetricsProvider technicalMetricsProvider;

    protected AbstractDailyTradingStrategy(OrderProgressRepository orderProgressRepository, TradeNotifier tradeNotifier,
                                           OrderRouterFactory orderRouterFactory, MarketFeed marketFeed,
                                           TechnicalMetricsProvider technicalMetricsProvider) {
        this.orderProgressRepository = orderProgressRepository;
        this.tradeNotifier = tradeNotifier;
        this.orderRouterFactory = orderRouterFactory;
        this.marketFeed = marketFeed;
        this.technicalMetricsProvider = technicalMetricsProvider;
    }

    @Override
    public void initialiseTrade(Order order, Map<String, TechnicalMetrics> metrics) {
        updateAtr(order, metrics);
        if (order.getOrderStatus() != OrderStatus.PENDING || (order.getEntry() != null && StringUtils.isNotBlank(order.getEntry().getBrokerOrderId()))) {
            log.warn("MTF order exists for user {} symbol {}", order.getUserId(), order.getSymbol());
            return;
        }

        try {
            Double ltp = resolveEntryLtp(order);

            var orderRouter = orderRouterFactory.getRouter(order.getBroker());
            var req = TradeOrderRequest.builder().symbol(order.getSymbol()).quantity(order.getQuantity())
                    .transactionType(Constants.TRANSACTION_TYPE_BUY)
                    .price(ltp == null ? null : HelperUtil.fixToTick(ltp * PRE_MARKET_LIMIT_MULTIPLIER)).build();
            var res = orderRouter.placePreMarketOrder(order.getUserId(), req);
            order.setEntry(Order.ExecutionRecord.builder().brokerOrderId(res.getOrderId()).build());
            order.setOrderStatus(OrderStatus.PLACED);
            log.info("MTF order placed for user {} symbol {} at init", order.getUserId(), order.getSymbol());
            tradeNotifier.orderPlaced(order.getUserId(), order.getQuantity(), order.getSymbol());
        } catch (Exception e) {
            order.setOrderStatus(OrderStatus.FAILED);
            log.error("Failed to place MTF order for user {} symbol {} error {} at init", order.getUserId(), order.getSymbol(), e.getMessage());
        }

        orderProgressRepository.saveProgress(order);
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
                tradeNotifier.buyExecuted(order.getUserId(), order.getQuantity(), order.getSymbol(),
                        orderDetails.getAveragePrice().doubleValue());
            }
        } catch (Exception e) {
            log.error("Failed to update MTF status for user {} symbol {} error {} at update", order.getUserId(), order.getSymbol(), e.getMessage());
            return;
        }

        orderProgressRepository.saveProgress(order);
    }

    /**
     * @return the reference price for the pre-market limit, or null to fall back to a market order.
     */
    private Double resolveEntryLtp(Order order) {
        var token = order.getMargin().getToken();
        return switch (marketFeed.getLtp(token)) {
            case Ltp.Price(double value) -> value;
            case Ltp.NotSubscribed _ -> subscribeAndAwaitLtp(order, token);
            // Subscribing to a dead socket cannot produce a price, so don't burn a second waiting on it.
            case Ltp.FeedDown _ -> {
                log.warn("Market feed is down; placing pre-market order for {} without a limit price", order.getSymbol());
                yield null;
            }
        };
    }

    private Double subscribeAndAwaitLtp(Order order, String token) {
        try {
            marketFeed.subscribe(token, ExchangeType.NSE.getValue());
        } catch (Exception _) {
            log.error("WS Subscription failed for {}", order.getSymbol());
            return null;
        }

        HelperUtil.pollWait(SUBSCRIBE_SETTLE_MILLIS);
        return marketFeed.getLtp(token) instanceof Ltp.Price(double value) ? value : null;
    }

    /** Attaches the symbol's ATR, reusing the run's shared map so one symbol costs one lookup. */
    private void updateAtr(Order order, Map<String, TechnicalMetrics> metrics) {
        try {
            var res = metrics.computeIfAbsent(order.getSymbol(), technicalMetricsProvider::atrFor);
            if (res != null) {
                order.setAtr(res);
            }
        } catch (Exception _) {
            log.error("Error updating ATR for {} orderId {}", order.getSymbol(), order.getId());
        }
    }

}
