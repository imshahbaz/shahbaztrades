package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.model.dto.angelone.websocket.Ltp;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.strategy.TargetStockResult;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.ExchangeType;
import com.app.shahbaztrades.service.MarketFeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Picks which stock from a signal to buy and how many shares, given the capital on the order and
 * the broker's leverage. Takes the first candidate that prices and sizes to a whole share.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeCandidateSelector {

    private static final int LTP_POLL_ATTEMPTS = 10;
    private static final long LTP_POLL_INTERVAL_MS = 100;
    private static final int CAPITAL_DIVISION_SCALE = 8;

    private final MarketFeed marketFeed;
    private final BrokerMarginPolicyFactory brokerMarginPolicyFactory;

    /** @return the chosen stock and quantity, or null if nothing in the signal is tradable. */
    public TargetStockResult select(ChartInkBacktestMarginDto signal, BigDecimal orderAmount, BrokerType brokerType) {
        try {
            var policy = brokerMarginPolicyFactory.getPolicy(brokerType);

            var candidates = signal.getMargins();
            if (candidates.size() > 1) {
                candidates = policy.rankCandidates(candidates);
            }

            for (var margin : candidates) {
                var target = size(margin, orderAmount, policy);
                if (target != null) {
                    return target;
                }
            }
        } catch (InterruptedException e) {
            log.error("Interrupted processing signal", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing signal", e);
        }

        return null;
    }

    private TargetStockResult size(Margin target, BigDecimal orderAmount, BrokerMarginPolicy policy)
            throws InterruptedException {
        Double ltp = resolveLtp(target.getToken());
        if (ltp == null) {
            return null;
        }

        BigDecimal leverage = policy.leverageFor(target);
        if (leverage == null || leverage.signum() <= 0) {
            return null;
        }

        int quantity = orderAmount.divide(BigDecimal.valueOf(ltp), CAPITAL_DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(leverage)
                .setScale(0, RoundingMode.DOWN)
                .intValue();

        return quantity > 0 ? new TargetStockResult(target, quantity) : null;
    }

    /** @return the live price, or null if the feed is down or the token never ticked. */
    private Double resolveLtp(String token) throws InterruptedException {
        return switch (marketFeed.getLtp(token)) {
            case Ltp.Price(double value) -> value;
            case Ltp.FeedDown _ -> null;
            case Ltp.NotSubscribed _ -> {
                marketFeed.subscribe(token, ExchangeType.NSE.getValue());
                yield awaitLtp(token);
            }
        };
    }

    private Double awaitLtp(String token) throws InterruptedException {
        for (int attempt = 0; attempt <= LTP_POLL_ATTEMPTS; attempt++) {
            switch (marketFeed.getLtp(token)) {
                case Ltp.Price(double value) -> {
                    return value;
                }
                // The socket died mid-wait; further polling cannot succeed.
                case Ltp.FeedDown _ -> {
                    return null;
                }
                case Ltp.NotSubscribed _ -> {
                    if (attempt < LTP_POLL_ATTEMPTS) {
                        Thread.sleep(LTP_POLL_INTERVAL_MS);
                    }
                }
            }
        }
        return null;
    }
}
