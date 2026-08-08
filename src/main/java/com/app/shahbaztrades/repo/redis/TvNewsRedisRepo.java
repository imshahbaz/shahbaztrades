package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class TvNewsRedisRepo extends RedisCache {

    private static final String KEY_PREFIX = "tv_news";

    public TvNewsRedisRepo(RedissonClient redissonClient) {
        super(redissonClient, KEY_PREFIX);
    }
}
