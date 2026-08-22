package com.app.shahbaztrades.components.zerodha;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds and caches a per-user {@link KiteConnect}.
 * <p>
 * The cache is an instance field rather than a constant on an interface, so it is scoped to this
 * bean and cannot be reached — or mutated — from anywhere that merely imports the API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZerodhaClientFactory {

    private final Cache<Long, KiteConnect> clientCache = new Cache<>();
    private final UserService userService;
    private final ZerodhaTokenStore tokenStore;

    /**
     * @throws NotFoundException if the user has no stored access token, which is the normal state
     *                           before the day's broker login.
     */
    public KiteConnect forUser(Long userId) {
        var cached = clientCache.get(userId);
        if (cached != null) {
            return cached;
        }

        var accessToken = tokenStore.find(userId)
                .orElseThrow(() -> new NotFoundException("Access token not found in redis for user " + userId));

        KiteConnect client = create(accessToken, userId);
        clientCache.set(userId, client, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        return client;
    }

    public KiteConnect create(String accessToken, Long userId) {
        log.info("Initiating KiteConnect for User ID: {}", userId);

        var config = requireUser(userId).getZerodhaConfig();
        if (!BrokerConfigValidator.validateZerodhaConfig(config)) {
            throw new NotFoundException("Zerodha API configuration is missing for this user");
        }

        KiteConnect client = new KiteConnect(config.getApiKey());
        client.setAccessToken(accessToken);
        return client;
    }

    /** Drops the cached client so the next call rebuilds it against a fresh token. */
    public void evict(Long userId) {
        clientCache.remove(userId);
    }

    private User requireUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }
        return user;
    }
}
