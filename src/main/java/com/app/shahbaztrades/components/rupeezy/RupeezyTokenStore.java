package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.repo.redis.RupeezyTokenCacheRedisRepo;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Where a user's Rupeezy access token lives: in-process first, Redis behind it so a restart or a
 * second instance does not force everyone to log in again.
 * <p>
 * The in-process tier is an instance field rather than a constant on an interface, so it is scoped
 * to this bean and cannot be mutated from anywhere that merely imports the API.
 */
@Component
@RequiredArgsConstructor
public class RupeezyTokenStore {

    private final Cache<Long, RupeezyTokenCache> localCache = new Cache<>();
    private final RupeezyTokenCacheRedisRepo<RupeezyTokenCache> rupeezyTokenCacheRedisRepo;

    /** @return the token, or null when the user has not authenticated with Rupeezy today. */
    public RupeezyTokenCache find(long userId) {
        var cache = localCache.get(userId);
        if (cache == null) {
            cache = rupeezyTokenCacheRedisRepo.get(String.valueOf(userId));
            if (cache != null) {
                localCache.set(userId, cache, tokenTtl());
            }
        }
        return cache;
    }

    public void save(long userId, RupeezyTokenCache cache) {
        localCache.set(userId, cache, tokenTtl());
        rupeezyTokenCacheRedisRepo.set(String.valueOf(userId), cache, tokenTtl());
    }

    public void delete(long userId) {
        rupeezyTokenCacheRedisRepo.delete(String.valueOf(userId));
        localCache.remove(userId);
    }

    /** Tokens expire with the trading day. */
    private Duration tokenTtl() {
        return Duration.ofSeconds(DateUtil.zerodhaTokenExpiry());
    }
}
