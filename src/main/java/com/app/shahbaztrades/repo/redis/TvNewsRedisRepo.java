package com.app.shahbaztrades.repo.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TvNewsRedisRepo<T> extends RedisCache<T> {

    private static final String KEY_PREFIX = "tv_news";

    public TvNewsRedisRepo(@Qualifier("redisTemplateObject") RedisTemplate<String, T> redisTemplate) {
        super(KEY_PREFIX, redisTemplate);
    }
}
