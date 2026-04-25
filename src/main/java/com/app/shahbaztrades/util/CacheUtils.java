package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.dto.chartink.ChartInkBacktestMarginDto;
import com.app.shahbaztrades.model.enums.CacheType;
import com.app.shahbaztrades.model.enums.OtpFor;
import com.zerodhatech.kiteconnect.KiteConnect;

import java.time.Duration;
import java.util.List;

public class CacheUtils {

    public static Cache<Long, KiteConnect> kiteClientCache = new Cache<>();
    public static Cache<String, List<ChartInkBacktestMarginDto>> pollerCache = new Cache<>();

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
