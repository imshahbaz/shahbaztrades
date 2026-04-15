package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.enums.CacheType;
import com.app.shahbaztrades.model.enums.OtpFor;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.zerodhatech.kiteconnect.KiteConnect;
import org.checkerframework.checker.index.qual.NonNegative;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class CacheUtils {

    public static Cache<Long, CachedKiteClient> kiteClientCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfter(new Expiry<Long, CachedKiteClient>() {
                @Override
                public long expireAfterCreate(Long key, CachedKiteClient value, long currentTime) {
                    return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
                }

                @Override
                public long expireAfterUpdate(Long key, CachedKiteClient value, long currentTime, @NonNegative long currentDuration) {
                    return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
                }

                @Override
                public long expireAfterRead(Long key, CachedKiteClient value, long currentTime, @NonNegative long currentDuration) {
                    return currentDuration;
                }

            })
            .build();

    public static CacheConfig getKeyAndExpiry(String reqId, CacheType cacheType) {
        String prefix = cacheType.name() + "_";

        return switch (cacheType) {
            case TRUECALLER -> new CacheConfig(prefix + reqId, Duration.ofMinutes(2));

            case SIGNUP, CRED_UPDATE -> new CacheConfig(prefix + reqId, Duration.ofMinutes(5));

        };
    }

    public static String getOtpCacheKey(String email, OtpFor otpFor) {
        return switch (otpFor) {
            case REGISTER -> email;
            case UPDATE -> email + "_update";
        };
    }

    public record CachedKiteClient(KiteConnect client, long ttlSeconds) {
    }

    public record CacheConfig(String key, Duration expiry) {
    }

}
