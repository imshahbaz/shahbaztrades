package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class MarketTickerRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "angel_one_ltp";

    public MarketTickerRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
