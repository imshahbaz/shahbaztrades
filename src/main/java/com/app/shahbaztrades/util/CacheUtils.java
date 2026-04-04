package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.enums.CacheType;
import com.app.shahbaztrades.model.enums.OtpFor;

import java.time.Duration;

public class CacheUtils {

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

    public record CacheConfig(String key, Duration expiry) {
    }

}
