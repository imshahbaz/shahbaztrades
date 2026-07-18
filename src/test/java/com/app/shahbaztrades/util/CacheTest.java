package com.app.shahbaztrades.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTest {

    @Test
    void setIfAbsent_firstCallWinsSubsequentCallsLose() {
        Cache<String, Boolean> cache = new Cache<>();
        assertTrue(cache.setIfAbsent("k", Boolean.TRUE, Duration.ofMinutes(1)));
        assertFalse(cache.setIfAbsent("k", Boolean.TRUE, Duration.ofMinutes(1)));
        assertEquals(Boolean.TRUE, cache.get("k"));
    }

    @Test
    void setIfAbsent_isAtomicUnderConcurrency_onlyOneWinnerPerKey() throws Exception {
        Cache<String, Boolean> cache = new Cache<>();
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger winners = new AtomicInteger();

        List<Callable<Void>> tasks = IntStream.range(0, threads).<Callable<Void>>mapToObj(i -> () -> {
            barrier.await();
            if (cache.setIfAbsent("order-1", Boolean.TRUE, Duration.ofMinutes(1))) {
                winners.incrementAndGet();
            }
            return null;
        }).toList();

        for (Future<Void> f : pool.invokeAll(tasks)) {
            f.get();
        }
        pool.shutdown();

        // Exactly one thread may claim the key — this is the guarantee the duplicate-order fix relies on.
        assertEquals(1, winners.get());
    }
}
