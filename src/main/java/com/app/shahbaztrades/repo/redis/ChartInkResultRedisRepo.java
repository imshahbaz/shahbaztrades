package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class ChartInkResultRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "chartink_result";

    public ChartInkResultRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
