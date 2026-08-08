package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class HoldingsDataRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "holdings";

    public HoldingsDataRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
