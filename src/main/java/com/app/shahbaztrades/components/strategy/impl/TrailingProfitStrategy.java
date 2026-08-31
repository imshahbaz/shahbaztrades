package com.app.shahbaztrades.components.strategy.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.analysis.TechnicalMetricsProvider;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.strategy.AbstractDailyTradingStrategy;
import com.app.shahbaztrades.components.trading.TradeNotifier;
import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.repo.OrderProgressRepository;
import com.app.shahbaztrades.service.MarketFeed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TrailingProfitStrategy extends AbstractDailyTradingStrategy {

    private static final String NAME = "TRAILING PROFIT";

    private final TradeWatchdog tradeWatchdog;

    public TrailingProfitStrategy(OrderProgressRepository orderProgressRepository, TradeNotifier tradeNotifier,
                                     OrderRouterFactory orderRouterFactory, MarketFeed marketFeed,
                                     TechnicalMetricsProvider technicalMetricsProvider, TradeWatchdog tradeWatchdog) {
        super(orderProgressRepository, tradeNotifier, orderRouterFactory, marketFeed, technicalMetricsProvider);
        this.tradeWatchdog = tradeWatchdog;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void startTrading(Order order) {
        if (!order.hasEntryPrice()) {
            log.info("Watchdog skipped order {} doesn't have entry price for user {} symbol {}", order.getId(), order.getUserId(), order.getSymbol());
            return;
        }

        try {
            marketFeed.subscribe(order.getMargin().getToken(), ExchangeType.NSE.getValue());
        } catch (Exception _) {
            log.error("WS Subscription failed for {}", order.getSymbol());
            return;
        }

        tradeWatchdog.watchMtfTrade(ActiveMtfTrade.builder()
                .order(order)
                .peakPrice(order.getEntry().getAveragePrice().doubleValue())
                .build());
    }
}
