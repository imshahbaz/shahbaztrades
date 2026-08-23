package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Where a user's Zerodha access token lives. Tokens expire with the trading day, so the TTL is set
 * from {@link DateUtil#zerodhaTokenExpiry()} rather than a fixed duration.
 */
@Component
@RequiredArgsConstructor
public class ZerodhaTokenStore {

    private static final String KEY_PREFIX = "zerodha_token:";

    private final StringRedisTemplate stringRedisTemplate;

    public Optional<String> find(long userId) {
        var token = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
        return StringUtils.isEmpty(token) ? Optional.empty() : Optional.of(token);
    }

    public void save(long userId, String accessToken) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + userId, accessToken,
                Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
    }

    public void delete(long userId) {
        stringRedisTemplate.delete(KEY_PREFIX + userId);
    }
}
