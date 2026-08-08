package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class GenAiRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "genai";
    private static final String LOCK_KEY = KEY_PREFIX + "_lock:";

    public GenAiRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, "genai");
    }

    public RLock getLock(String id) {
        return redissonClient.getLock(LOCK_KEY + id);
    }
}
