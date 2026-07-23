package com.app.shahbaztrades.components.angelone;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("UnstableApiUsage")
public class AngelOneRateLimiter {

    private static final double HISTORICAL_DATA_PERMITS_PER_SECOND = 1.0;

    private final RateLimiter historicalDataRateLimiter = RateLimiter.create(HISTORICAL_DATA_PERMITS_PER_SECOND);

    public void acquireHistoricalData() {
        historicalDataRateLimiter.acquire();
    }
}
