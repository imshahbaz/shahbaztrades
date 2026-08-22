package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.sessionmanager.SessionManagerClient;
import com.app.shahbaztrades.components.zerodha.ZerodhaAutoLoginLock;
import com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.fcm.NotificationRequest;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginRequestDTO;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaAutoLoginService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.HelperUtil;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZerodhaAutoLoginServiceImpl implements ZerodhaAutoLoginService {

    private static final String AUTH_CACHE = "zerodhaAuthCache";
    private static final String TOKEN_IN_PROGRESS = "Token generation already in progress";

    private final UserService userService;
    private final ZerodhaService zerodhaService;
    private final ZerodhaClientFactory zerodhaClientFactory;
    private final ZerodhaAutoLoginLock autoLoginLock;
    private final SessionManagerClient sessionManagerClient;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void autoLogin(Set<Long> userIds) {
        var users = userService.findByIds(userIds);
        if (users.isEmpty()) {
            log.info("Users not found for zerodha auto login");
            return;
        }

        for (User user : users) {
            if (user.isZerodhaAutoLoginEnabled()) {
                HelperUtil.EXECUTOR.execute(() -> attemptAutoLogin(user));
            } else {
                remindToLogIn(user);
            }
        }
    }

    @Override
    public void autoConnectZerodhaSession(User user) {
        if (!user.isZerodhaAutoLoginEnabled()) {
            autoLoginLock.release(user.getUserId());
            throw new BadRequestException("Auto login is not enabled");
        }

        var res = sessionManagerClient.autoLogin(
                ZerodhaLoginRequestDTO.mapDto(user.getUserId(), user.getZerodhaConfig()), SessionManagerClient.SOURCE);

        if (res.isPending() && res.message().equals(TOKEN_IN_PROGRESS)) {
            throw new ResourceAlreadyExistsException("Request already exists");
        }

        if (res.isError()) {
            log.error("Auto login failed at session manager{}", user.getUserId());
            autoLoginLock.release(user.getUserId());
            throw new BadRequestException("Auto login failed at session manager");
        }
    }

    @Override
    @Async("taskExecutor")
    public void sessionManagerCallback(ZerodhaLoginResponseDTO request) {
        try {
            if (request.isError() || StringUtils.isEmpty(request.requestToken())) {
                return;
            }

            zerodhaService.login(new BrokerLoginDto(request.requestToken(), request.userid()));
        } catch (Exception e) {
            log.error("Session Manager callback exception {}", request.requestToken(), e);
        } finally {
            // Always release, so a failed callback cannot wedge the user out of logging in again.
            autoLoginLock.release(request.userid());
            var cache = cacheManager.getCache(AUTH_CACHE);
            if (cache != null) {
                cache.evict(request.userid());
            }
        }
    }

    /** Skips the session manager entirely when the user's existing token still works. */
    private void attemptAutoLogin(User user) {
        var userId = user.getUserId();
        try {
            zerodhaClientFactory.forUser(userId).getProfile();
            return;
        } catch (NotFoundException _) {
            log.info("Access token not found proceeding with auto login {}", userId);
        } catch (IOException | KiteException _) {
            log.info("Kite connection failed proceeding with auto login {}", userId);
        } catch (Exception e) {
            log.info("Auto login failed {} {}", userId, e.getMessage());
            return;
        }

        try {
            autoConnectZerodhaSession(user);
        } catch (Exception e) {
            log.info("Auto login failed {} {}", userId, e.getMessage());
        }
    }

    private void remindToLogIn(User user) {
        applicationEventPublisher.publishEvent(NotificationRequest.builder()
                .userId(user.getUserId())
                .title(Constants.NOTIFICATION_TITLE_BROKER_LOGIN)
                .body(Constants.NOTIFICATION_MESSAGE_BROKER_LOGIN)
                .data(Collections.emptyMap())
                .build());
    }
}
