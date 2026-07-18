package com.app.shahbaztrades.util;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Set;

public class Cache<K, V> {

    private static final long MAXIMUM_SIZE = 50_000;
    private final com.github.benmanes.caffeine.cache.Cache<K, Holder<V>> cache = Caffeine.newBuilder()
            .maximumSize(MAXIMUM_SIZE)
            .expireAfter(new Expiry<K, Holder<V>>() {
                @Override
                public long expireAfterCreate(@NonNull K key, @NonNull Holder<V> holder, long currentTime) {
                    return holder.ttlNanos();
                }

                @Override
                public long expireAfterUpdate(@NonNull K key, @NonNull Holder<V> holder, long currentTime, long currentDuration) {
                    return holder.ttlNanos();
                }

                @Override
                public long expireAfterRead(@NonNull K key, @NonNull Holder<V> holder, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public void set(K key, V value) {
        cache.put(key, new Holder<>(value, Long.MAX_VALUE));
    }

    public void set(K key, V value, Duration expiry) {
        cache.put(key, new Holder<>(value, expiry.toNanos()));
    }

    public boolean setIfAbsent(K key, V value, Duration expiry) {
        return cache.asMap().putIfAbsent(key, new Holder<>(value, expiry.toNanos())) == null;
    }

    public V get(K key) {
        var holder = cache.getIfPresent(key);
        return holder == null ? null : holder.data();
    }

    public void remove(K key) {
        cache.invalidate(key);
    }

    public Set<K> getActiveKeys() {
        return cache.asMap().keySet();
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private record Holder<V>(V data, long ttlNanos) {
    }

}
