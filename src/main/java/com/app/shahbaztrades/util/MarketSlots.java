package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarketSlots {

    /**
     * The times of day a periodic job should act on, inclusive of both ends.
     * <p>
     * Generated rather than written out, so a schedule cannot drift out of step through a typo in
     * one of two dozen literals.
     */
    public static Set<LocalTime> every(LocalTime first, LocalTime last, Duration step) {
        Set<LocalTime> slots = new LinkedHashSet<>();
        for (LocalTime slot = first; !slot.isAfter(last); slot = slot.plus(step)) {
            slots.add(slot);
        }
        return Set.copyOf(slots);
    }
}
