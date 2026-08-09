package com.app.shahbaztrades.repo.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RupeezyTokenCacheRedisRepo<T> extends RedisCache<T> {

    private static final String KEY_PREFIX = "rupeezy_token";

    public RupeezyTokenCacheRedisRepo(@Qualifier("redisTemplateObject") RedisTemplate<String, T> redisTemplate) {
        super(KEY_PREFIX, redisTemplate);
    }
}
