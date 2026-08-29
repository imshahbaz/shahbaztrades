package com.app.shahbaztrades.components.polling;

import com.app.shahbaztrades.components.marketdata.BarSeriesStore;
import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.enums.PollerType;
import com.app.shahbaztrades.components.strategy.StrategyRegistry;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.MarketSlots;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Evaluates strategies against our own bar series, on the bar boundary itself. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStrategySignalPoller implements SignalPoller {

    private static final Set<LocalTime> SLOTS =
            MarketSlots.every(LocalTime.of(9, 30), LocalTime.of(15, 0), Duration.ofMinutes(15));
    /** Lets the closing tick land in the series before it is read. */
    private static final long BAR_SETTLE_SECONDS = 1;
    private static final int BAR_MINUTES = 15;

    private final StrategyRegistry strategyRegistry;
    private final BarSeriesStore barSeriesStore;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PollerType getType() {
        return PollerType.LOCAL_STRATEGY;
    }

    @Override
    public boolean firesAt(LocalTime now) {
        return SLOTS.contains(now);
    }

    @Override
    public void poll(String strategyName) {
        log.info("Target match at time {} ! Fetching manual signals for {}", LocalTime.now(DateUtil.IST_ZONE), strategyName);

        try {
            var tokens = strategyRegistry.getTokensForStrategy(strategyName);
            if (CollectionUtils.isEmpty(tokens)) {
                return;
            }

            TimeUnit.SECONDS.sleep(BAR_SETTLE_SECONDS);
            var strategy = strategyRegistry.getStrategyInstance(strategyName);
            var barSeriesList = tokens.stream().map(barSeriesStore::snapshot).toList();
            var signals = strategy.getFilteredMargins(barSeriesList, strategyRegistry.getTokenSymbolMap());
            if (CollectionUtils.isEmpty(signals)) {
                return;
            }

            log.info("Complete manual signals list: {} for strategy: {}", signals, strategyName);
            // Stamped at the bar that just closed, matching how ChartInk timestamps its signals.
            eventPublisher.publishEvent(new ChartInkSignalEvent(strategyName, List.of(ChartInkBacktestMarginDto.builder()
                    .marketTime(DateUtil.getCurrentDateTime().minusMinutes(BAR_MINUTES))
                    .margins(signals)
                    .build())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Manual fetch interrupted", e);
        } catch (Exception e) {
            log.error("Manual fetch failed", e);
        }
    }
}
