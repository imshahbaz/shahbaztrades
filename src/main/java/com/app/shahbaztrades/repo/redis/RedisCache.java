package com.app.shahbaztrades.repo.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collection;

public abstract class RedisCache<T> {
    private final String keyPrefix;
    private final RedisTemplate<String, T> redisTemplate;

    protected RedisCache(String keyPrefix, RedisTemplate<String, T> redisTemplate) {
        this.keyPrefix = keyPrefix;
        this.redisTemplate = redisTemplate;
    }

    private String key(String id) {
        return keyPrefix + ":" + id;
    }

    public T get(String id) {
        return redisTemplate.opsForValue().get(key(id));
    }

    public void set(String id, T value, Duration ttl) {
        redisTemplate.opsForValue().set(key(id), value, ttl);
    }

    public boolean exists(String id) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(id)));
    }

    public void delete(String id) {
        redisTemplate.delete(key(id));
    }

    public void deleteAll(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        redisTemplate.delete(ids.stream()
                .map(this::key)
                .toList());
    }

}