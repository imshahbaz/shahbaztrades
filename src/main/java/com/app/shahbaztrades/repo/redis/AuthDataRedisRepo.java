package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class AuthDataRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "auth";

    public AuthDataRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
