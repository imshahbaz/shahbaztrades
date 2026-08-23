package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.util.Constants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Marks an auto-login as in flight so a second attempt cannot start while the session manager is
 * still working. A TOTP login burns a one-time code, so overlapping attempts fail each other.
 */
@Component
@RequiredArgsConstructor
public class ZerodhaAutoLoginLock {

    private final StringRedisTemplate stringRedisTemplate;

    public boolean isPending(long userId) {
        return !StringUtils.isEmpty(
                stringRedisTemplate.opsForValue().get(Constants.ZERODHA_AUTO_LOGIN_KEY + userId));
    }

    public void release(long userId) {
        stringRedisTemplate.delete(Constants.ZERODHA_AUTO_LOGIN_KEY + userId);
    }
}
