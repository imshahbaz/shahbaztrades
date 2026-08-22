package com.app.shahbaztrades.components.polling;

import com.app.shahbaztrades.model.enums.PollerType;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Runs at most one poller per strategy for the session, waking every minute and asking the poller
 * whether this minute is one of its slots.
 * <p>
 * What to fetch and when belongs to the {@link SignalPoller}; this only owns scheduling and the
 * one-per-strategy guarantee.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PollingHelper {

    private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, ScheduledFuture<?>> runningPollers = new ConcurrentHashMap<>();
    private final SignalPollerFactory signalPollerFactory;
    private final TaskScheduler taskScheduler;

    /** Idempotent per strategy: a second call while one is running is a no-op. */
    public void runPollerTask(String strategyName, PollerType type) {
        runningPollers.computeIfAbsent(strategyName, name -> {
            log.info("Watchdog poller started for strategy {} using {}", name, type);
            var poller = signalPollerFactory.getPoller(type);
            return taskScheduler.scheduleAtFixedRate(() -> tick(name, poller), TICK_INTERVAL);
        });
    }

    private void tick(String strategyName, SignalPoller poller) {
        if (DateUtil.isSquareOffTimeReached()) {
            log.info("Market closed. Watchdog exiting.");
            stop(strategyName);
            return;
        }

        LocalTime now = LocalTime.now(DateUtil.IST_ZONE).withSecond(0).withNano(0);
        if (!poller.firesAt(now)) {
            return;
        }

        try {
            poller.poll(strategyName);
        } catch (Exception e) {
            log.error("Execution failed for poller {} type {}", strategyName, poller.getType(), e);
        }
    }

    private void stop(String strategyName) {
        var task = runningPollers.remove(strategyName);
        if (task != null) {
            task.cancel(false);
        }
    }
}
