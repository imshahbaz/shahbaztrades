package com.app.shahbaztrades.util;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.*;

@Slf4j
public class Cache<K, V> {
    private final ScheduledExecutorService cleanupService = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<K, DataHolder<V>> cacheMap = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ExpiryNode<K>> expiryQueue = new PriorityBlockingQueue<>();

    public Cache() {
        startCleanup();
    }

    private void startCleanup() {
        cleanupService.scheduleWithFixedDelay(() -> {
            try {
                var now = System.currentTimeMillis();
                while (true) {
                    ExpiryNode<K> node = expiryQueue.peek();
                    if (node == null || node.expiryTime() > now) {
                        break;
                    }

                    expiryQueue.poll();
                    cacheMap.computeIfPresent(node.key(), (k, holder) ->
                            holder.getExpiryTime() <= now ? null : holder
                    );
                }
            } catch (Exception e) {
                log.error("Error occurred while cleaning up {}", e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void set(K key, V value) {
        cacheMap.put(key, DataHolder.<V>builder()
                .data(value)
                .expiryTime(-1)
                .build());
    }

    public V get(K key) {
        var holder = cacheMap.get(key);
        if (holder == null) {
            return null;
        }
        if (holder.getExpiryTime() > 0 && holder.getExpiryTime() < System.currentTimeMillis()) {
            cacheMap.remove(key, holder);
            return null;
        }
        return holder.data;
    }

    public void remove(K key) {
        cacheMap.remove(key);
    }

    public void set(K key, V value, Duration expiry) {
        var expiryTime = System.currentTimeMillis() + expiry.toMillis();
        cacheMap.put(key, DataHolder.<V>builder()
                .data(value)
                .expiryTime(expiryTime)
                .build());
        expiryQueue.offer(new ExpiryNode<>(key, expiryTime));
    }

    @Data
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static class DataHolder<T> {
        T data;
        long expiryTime;
    }

    public record ExpiryNode<K>(K key, long expiryTime) implements Comparable<ExpiryNode<K>> {
        @Override
        public int compareTo(ExpiryNode<K> other) {
            return Long.compare(this.expiryTime, other.expiryTime);
        }
    }

}
