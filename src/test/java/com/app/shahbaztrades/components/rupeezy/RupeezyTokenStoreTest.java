package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.repo.redis.RupeezyTokenCacheRedisRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The two-tier token cache: in-process in front of Redis. */
@ExtendWith(MockitoExtension.class)
class RupeezyTokenStoreTest {

    @Mock
    private RupeezyTokenCacheRedisRepo<RupeezyTokenCache> rupeezyTokenCacheRedisRepo;

    private RupeezyTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RupeezyTokenStore(rupeezyTokenCacheRedisRepo);
    }

    @Test
    void find_promotesTheRedisEntryIntoTheLocalCache() {
        var cached = new RupeezyTokenCache();
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(cached);

        assertSame(cached, store.find(7L));
        // Second read must be served locally, not from Redis again.
        assertSame(cached, store.find(7L));
        verify(rupeezyTokenCacheRedisRepo).get("7");
    }

    @Test
    void find_returnsNullWhenNeitherLayerHasAToken() {
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(null);

        assertNull(store.find(7L));
    }

    @Test
    void save_writesThroughToBothLayers() {
        var cache = new RupeezyTokenCache();

        store.save(7L, cache);

        verify(rupeezyTokenCacheRedisRepo).set(eq("7"), eq(cache), any(Duration.class));
        assertSame(cache, store.find(7L));
    }

    @Test
    void delete_clearsBothLayers() {
        store.save(7L, new RupeezyTokenCache());

        store.delete(7L);

        verify(rupeezyTokenCacheRedisRepo).delete("7");
        when(rupeezyTokenCacheRedisRepo.get("7")).thenReturn(null);
        assertNull(store.find(7L));
    }
}
