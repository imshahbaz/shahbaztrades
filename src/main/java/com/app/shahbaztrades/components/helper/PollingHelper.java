package com.app.shahbaztrades.components.helper;

import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.dto.chartink.ChartInkSignalEvent;
import com.app.shahbaztrades.service.ChartInkService;
import com.app.shahbaztrades.service.impl.StrategyRegistry;
import com.app.shahbaztrades.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PollingHelper {

    private static final Set<LocalTime> FIFTEEN_MIN_DELAYED_TARGETS = Set.of(
            LocalTime.of(9, 35), LocalTime.of(9, 50), LocalTime.of(10, 5), LocalTime.of(10, 20),
            LocalTime.of(10, 35), LocalTime.of(10, 50), LocalTime.of(11, 5), LocalTime.of(11, 20),
            LocalTime.of(11, 35), LocalTime.of(11, 50), LocalTime.of(12, 5), LocalTime.of(12, 20),
            LocalTime.of(12, 35), LocalTime.of(12, 50), LocalTime.of(13, 5), LocalTime.of(13, 20),
            LocalTime.of(13, 35), LocalTime.of(13, 50), LocalTime.of(14, 5), LocalTime.of(14, 20),
            LocalTime.of(14, 35), LocalTime.of(14, 50), LocalTime.of(15, 5)
    );

    private static final Set<LocalTime> FIFTEEN_MIN_TARGETS = Set.of(
            LocalTime.of(9, 15), LocalTime.of(9, 30), LocalTime.of(9, 45), LocalTime.of(10, 0),
            LocalTime.of(10, 15), LocalTime.of(10, 30), LocalTime.of(10, 45), LocalTime.of(11, 0),
            LocalTime.of(11, 15), LocalTime.of(11, 30), LocalTime.of(11, 45), LocalTime.of(12, 0),
            LocalTime.of(12, 15), LocalTime.of(12, 30), LocalTime.of(12, 45), LocalTime.of(13, 0),
            LocalTime.of(13, 15), LocalTime.of(13, 30), LocalTime.of(13, 45), LocalTime.of(14, 0),
            LocalTime.of(14, 15), LocalTime.of(14, 30), LocalTime.of(14, 45), LocalTime.of(15, 0)
    );

    private final Map<String, ScheduledFuture<?>> runningPollers = new ConcurrentHashMap<>();
    private final ChartInkService chartInkService;
    private final ApplicationEventPublisher eventPublisher;
    private final StrategyRegistry strategyRegistry;
    private final MarketDataContainer marketDataContainer;
    private final ThreadPoolTaskScheduler taskScheduler;

    public PollingHelper(ChartInkService chartInkService, ApplicationEventPublisher eventPublisher,
                         StrategyRegistry strategyRegistry, MarketDataContainer marketDataContainer) {
        this.chartInkService = chartInkService;
        this.eventPublisher = eventPublisher;
        this.strategyRegistry = strategyRegistry;
        this.marketDataContainer = marketDataContainer;
        this.taskScheduler = dynamicTaskScheduler();
    }

    public void runPollerTask(String name, boolean isDelayed) {
        runningPollers.computeIfAbsent(name, strategyName -> {
            log.info("Manual Watchdog Poller started for strategy {}", strategyName);
            Runnable task = () -> {
                try {
                    if (isDelayed) {
                        chartInkPoller(name);
                    } else {
                        manualPoller(name);
                    }
                } catch (Exception e) {
                    log.error("Execution failed for poller {} isDelayed {}", strategyName, isDelayed, e);
                }
            };

            return taskScheduler.scheduleAtFixedRate(task, Duration.ofMinutes(1));
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

        LocalTime now = LocalTime.now(DateUtil.IST_ZONE).withSecond(0).withNano(0);
        if (FIFTEEN_MIN_DELAYED_TARGETS.contains(now)) {
            log.info("Target match at time {} ! Fetching signals...", now);

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

        LocalTime now = LocalTime.now(DateUtil.IST_ZONE).withSecond(0).withNano(0);
        if (FIFTEEN_MIN_TARGETS.contains(now)) {
            log.info("Target match at time {} ! Fetching manual signals...", now);

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
                        .marketTime(DateUtil.getCurrentDateTime().minusMinutes(15))
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

    private ThreadPoolTaskScheduler dynamicTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("WatchdogPoller-");
        scheduler.initialize();
        return scheduler;
    }

}
