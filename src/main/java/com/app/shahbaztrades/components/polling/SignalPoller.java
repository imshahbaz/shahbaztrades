package com.app.shahbaztrades.components.polling;

import com.app.shahbaztrades.model.enums.PollerType;

import java.time.LocalTime;

/**
 * One way of discovering trade signals for a strategy, and the times of day it is worth asking.
 * <p>
 * Each source owns its own schedule because they do not line up: locally evaluated strategies can
 * act the moment a bar closes, while ChartInk publishes on a lag.
 */
public interface SignalPoller {

    PollerType getType();

    /** @param now the current IST time, truncated to the minute. */
    boolean firesAt(LocalTime now);

    /** Fetches signals and publishes them. Implementations contain their own failure handling. */
    void poll(String strategyName);
}
