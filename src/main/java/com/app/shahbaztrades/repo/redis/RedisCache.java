package com.app.shahbaztrades.repo.redis;

import org.redisson.api.RedissonClient;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collection;

public abstract class RedisCache {

    protected final RedissonClient redissonClient;
    private final String keyPrefix;

    protected RedisCache(RedissonClient redissonClient, String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = keyPrefix;
    }

    private String key(String id) {
        return keyPrefix + ":" + id;
    }

    public <T> T get(String id) {
        return redissonClient.<T>getBucket(key(id)).get();
    }

    public <T> void set(String id, T value, Duration ttl) {
        redissonClient.<T>getBucket(key(id)).set(value, ttl);
    }

    public boolean exists(String id) {
        return redissonClient.getBucket(key(id)).isExists();
    }

    public void delete(String id) {
        redissonClient.getBucket(key(id)).delete();
    }

    public void deleteAll() {
        redissonClient.getKeys().deleteByPattern(keyPrefix + ":*");
    }

    public void deleteAll(Collection<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        redissonClient.getKeys().delete(
                ids.stream()
                        .map(this::key)
                        .toArray(String[]::new)
        );
    }

}