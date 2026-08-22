package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.zerodha.ZerodhaAutoLoginLock;
import com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory;
import com.app.shahbaztrades.components.zerodha.ZerodhaTokenStore;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZerodhaServiceImpl implements ZerodhaService {

    private static final String AUTH_CACHE = "zerodhaAuthCache";

    private final UserService userService;
    private final ZerodhaClientFactory zerodhaClientFactory;
    private final ZerodhaTokenStore tokenStore;
    private final ZerodhaAutoLoginLock autoLoginLock;

    @Override
    @CacheEvict(value = AUTH_CACHE, key = "#request.userId")
    public void login(BrokerLoginDto request) {
        var token = generateAccessToken(request.requestToken(), request.userId());
        tokenStore.save(request.userId(), token);
        zerodhaClientFactory.evict(request.userId());
    }

    @Override
    @Cacheable(value = AUTH_CACHE, key = "#userDto.userId", sync = true)
    public ApiResponse<String> getAuth(UserDto userDto) {
        var user = requireUser(userDto.getUserId());

        if (!BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
            throw new NotFoundException("E001");
        }

        // An auto-login already in flight would be invalidated by a manual one starting now.
        if (user.getZerodhaConfig().isTotpEnabled() && autoLoginLock.isPending(userDto.getUserId())) {
            throw new ResourceAlreadyExistsException("E002");
        }

        try {
            zerodhaClientFactory.forUser(userDto.getUserId()).getProfile();
        } catch (NotFoundException | IOException | KiteException _) {
            return ApiResponse.<String>builder()
                    .success(Boolean.FALSE)
                    .data(user.getZerodhaConfig().getApiKey())
                    .message("Token expired")
                    .build();
        }

        return ApiResponse.ok(String.valueOf(user.getUserId()), "Token already exist");
    }

    @Override
    public Long setConfig(User.ZerodhaConfig config, UserDto userDto) {
        if (!BrokerConfigValidator.validateZerodhaConfig(config)) {
            throw new BadRequestException("Invalid request");
        }

        // A username without the TOTP pair cannot drive an unattended login.
        if (!StringUtils.isEmpty(config.getUserName())
                && StringUtils.isAnyEmpty(config.getPassword(), config.getTotpSecret())) {
            throw new BadRequestException("Invalid request");
        }

        if (!userService.updateZerodhaConfig(userDto.getUserId(), config)) {
            throw new UnauthorizedException("User not found");
        }

        return userDto.getUserId();
    }

    @Override
    public void revokeZerodhaAuth(long userId) {
        tokenStore.delete(userId);
        zerodhaClientFactory.evict(userId);
    }

    private String generateAccessToken(String requestToken, Long userId) {
        log.info("Generating access token for User ID: {}", userId);

        var user = requireUser(userId);
        if (!BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
            throw new BadRequestException("Zerodha config not found");
        }

        try (KiteConnect kc = new KiteConnect(user.getZerodhaConfig().getApiKey())) {
            var userSession = kc.generateSession(requestToken, user.getZerodhaConfig().getApiSecret());

            if (userSession == null || StringUtils.isEmpty(userSession.accessToken)) {
                throw new UnauthorizedException("Failed to generate session");
            }

            return userSession.accessToken;
        } catch (IOException | KiteException e) {
            log.error("Failed to generate access token", e);
            throw new UnauthorizedException("Failed to generate session");
        }
    }

    private User requireUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }
        return user;
    }
}
