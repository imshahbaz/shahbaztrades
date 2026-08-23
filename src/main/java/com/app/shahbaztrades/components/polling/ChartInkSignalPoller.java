package com.app.shahbaztrades.components.polling;

import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.model.enums.PollerType;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.util.MarketSlots;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Set;

/** Pulls signals from ChartInk's screener, which lags the bar close by 20 minutes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartInkSignalPoller implements SignalPoller {

    private static final Set<LocalTime> SLOTS =
            MarketSlots.every(LocalTime.of(9, 35), LocalTime.of(15, 5), Duration.ofMinutes(15));

    private final ChartInkService chartInkService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PollerType getType() {
        return PollerType.CHART_INK;
    }

    @Override
    public boolean firesAt(LocalTime now) {
        return SLOTS.contains(now);
    }

    @Override
    public void poll(String strategyName) {
        log.info("Target match at time {} ! Fetching signals...", LocalTime.now());

        try {
            var signals = chartInkService.fetchTodayBacktestDataWithMargin(strategyName);
            if (CollectionUtils.isEmpty(signals)) {
                return;
            }

            log.info("Complete signals list: {}", signals);
            eventPublisher.publishEvent(new ChartInkSignalEvent(strategyName, signals));
        } catch (Exception e) {
            log.error("Chart ink fetch failed", e);
        }
    }
}
