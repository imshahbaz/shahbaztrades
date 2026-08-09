package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class RupeezyTokenCacheRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "rupeezy_token";

    public RupeezyTokenCacheRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
