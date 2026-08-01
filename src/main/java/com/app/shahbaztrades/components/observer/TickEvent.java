package com.app.shahbaztrades.components.observer;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable ring-buffer slot carrying a single market tick (token + last traded price).
 * Instances are pre-allocated once by the Disruptor and reused for every event, so the
 * translator overwrites the fields in place instead of allocating per tick.
 */
@Getter
@Setter
public class TickEvent {
    private String token;
    private double ltp;
}
