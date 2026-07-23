package com.app.shahbaztrades.components.angelone;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AngelOneRateLimiter {

    private static final RateLimiterConfig HISTORICAL_DATA_CONFIG = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMinutes(10))
            .build();

    private final RateLimiter historicalDataRateLimiter =
            RateLimiter.of("angel-one-historical", HISTORICAL_DATA_CONFIG);

    public void acquireHistoricalData() {
        historicalDataRateLimiter.acquirePermission();
    }
}
