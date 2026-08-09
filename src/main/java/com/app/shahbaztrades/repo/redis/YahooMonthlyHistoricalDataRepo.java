package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class YahooMonthlyHistoricalDataRepo<T> extends RedisCache<T> {

    private static final String KEY_PREFIX = "yahoo_monthly_historical_data";
    private static final String LOCK_KEY = KEY_PREFIX + "_lock:";

    private final RedissonClient redissonClient;

    public YahooMonthlyHistoricalDataRepo(RedissonClient redissonClient, @Qualifier("redisTemplateObject") RedisTemplate<String, T> redisTemplate) {
        super(KEY_PREFIX, redisTemplate);
        this.redissonClient = redissonClient;
    }

    public RLock getLock(String id) {
        return redissonClient.getLock(LOCK_KEY + id);
    }
}
