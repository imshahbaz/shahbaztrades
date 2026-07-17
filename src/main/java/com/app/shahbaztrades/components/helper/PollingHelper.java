package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollingHelper {

    private static final DateTimeFormatter HOUR_MIN_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> FIFTEEN_MIN_DELAYED_TARGETS = Set.of(
            "09:35", "09:50", "10:05", "10:20", "10:35", "10:50",
            "11:05", "11:20", "11:35", "11:50", "12:05", "12:20",
            "12:35", "12:50", "13:05", "13:20", "13:35", "13:50",
            "14:05", "14:20", "14:35", "14:50", "15:05"
    );

    private static final Set<String> FIFTEEN_MIN_TARGETS = Set.of(
            "9:15", "09:30", "09:45", "10:00", "10:15", "10:30",
            "10:45", "11:00", "11:15", "11:30", "11:45", "12:00",
            "12:15", "12:30", "12:45", "13:00", "13:15", "13:30",
            "13:45", "14:00", "14:15", "14:30", "14:45", "15:00"
    );

    private final Map<String, ScheduledFuture<?>> runningPollers = new ConcurrentHashMap<>();
    private final ChartInkService chartInkService;
    private final ApplicationEventPublisher eventPublisher;
    private final StrategyRegistry strategyRegistry;
    private final MarketDataContainer marketDataContainer;

    public void runPollerTask(String name, boolean isDelayed) {
        runningPollers.computeIfAbsent(name, strategyName -> {
            log.info("Manual Watchdog Poller started for strategy {}", strategyName);
            return HelperUtil.SCHEDULER.scheduleAtFixedRate(() -> HelperUtil.EXECUTOR.execute(() -> {
                if (isDelayed) {
                    chartInkPoller(name);
                } else {
                    manualPoller(name);
                }
            }), 1, 1, TimeUnit.MINUTES);
        });
    }

    public void chartInkPoller(String name) {
        if (DateUtil.isSquareOffTimeReached()) {
            log.info("Market closed. Watchdog exiting.");
            var task = runningPollers.remove(name);
            if (task != null) {
                task.cancel(false);
            }
            return;
        }

        String currentTime = LocalTime.now(DateUtil.IST_ZONE).format(HOUR_MIN_FORMATTER);
        if (FIFTEEN_MIN_DELAYED_TARGETS.contains(currentTime)) {
            log.info("Target match at time {} ! Fetching signals...", currentTime);

            try {
                var signals = chartInkService.fetchTodayBacktestDataWithMargin(name);
                if (CollectionUtils.isEmpty(signals)) {
                    return;
                }

                log.info("Complete signals list: {}", signals);
                eventPublisher.publishEvent(new ChartInkSignalEvent(name, signals));
            } catch (Exception e) {
                log.error("Chart ink fetch failed", e);
            }
        }
    }

    private void manualPoller(String name) {
        if (DateUtil.isSquareOffTimeReached()) {
            log.info("Market closed. Manual Watchdog exiting.");
            var task = runningPollers.remove(name);
            if (task != null) {
                task.cancel(false);
            }
            return;
        }

        var now = DateUtil.getCurrentDateTime();
        String currentTime = now.format(HOUR_MIN_FORMATTER);
        if (FIFTEEN_MIN_TARGETS.contains(currentTime)) {
            log.info("Target match at time {} ! Fetching manual signals...", currentTime);

            try {
                var tokens = strategyRegistry.getTokensForStrategy(name);
                if (CollectionUtils.isEmpty(tokens)) {
                    return;
                }

                TimeUnit.SECONDS.sleep(1);
                var strategy = strategyRegistry.getStrategyInstance(name);
                var barSeriesList = tokens.stream().map(marketDataContainer::snapshotSeries).toList();
                var signals = strategy.getFilteredMargins(barSeriesList, strategyRegistry.getTokenSymbolMap());
                if (CollectionUtils.isEmpty(signals)) {
                    return;
                }

                log.info("Complete manual signals list: {}", signals);
                eventPublisher.publishEvent(new ChartInkSignalEvent(name, List.of(ChartInkBacktestMarginDto.builder()
                        .marketTime(now.minusMinutes(15))
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

}
