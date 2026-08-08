package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class YahooMonthlyHistoricalDataRepo extends RedisCache {

    private static final String KEY_PREFIX = "yahoo_monthly_historical_data";
    private static final String LOCK_KEY = KEY_PREFIX + "_lock:";

    public YahooMonthlyHistoricalDataRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }

    public RLock getLock(String id) {
        return redissonClient.getLock(LOCK_KEY + id);
    }
}
