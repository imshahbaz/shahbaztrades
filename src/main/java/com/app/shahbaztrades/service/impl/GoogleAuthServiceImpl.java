package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.auth.GoogleAuthUtils;
import com.app.shahbaztrades.components.auth.SessionCookieIssuer;
import com.app.shahbaztrades.config.security.JwtService;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCookieResponse;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.repo.redis.AuthDataRedisRepo;
import com.app.shahbaztrades.service.GoogleAuthService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserService userService;
    private final MongoConfigService mongoConfigService;
    private final GoogleAuthUtils googleAuthUtils;
    private final JwtService jwtService;
    private final SessionCookieIssuer sessionCookieIssuer;
    private final AuthDataRedisRepo<UserDto> authDataRedisRepo;
    private final AuthDataRedisRepo<User> userAuthDataRedisRepo;
    private final AsyncTaskExecutor taskExecutor;

    @Override
    public AuthCookieResponse<String> validateGoogleToken(String code, boolean nativeFlow) {
        if (nativeFlow) {
            var gUser = googleAuthUtils.validateIdToken(code);
            if (Objects.isNull(gUser)) {
                log.warn("Invalid Google Token");
                throw new BadRequestException("Invalid Google Token");
            }

            var user = userService.findOrCreateGoogleUser(gUser);
            String tokenStr = jwtService.generateToken(user.toDto());
            String cookie = sessionCookieIssuer.issue(tokenStr);
            return new AuthCookieResponse<>(tokenStr, "Google Token", cookie);
        }


        String id = UUID.randomUUID().toString();
        String signedUuid = AuthUtil.signState(id, mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());
        taskExecutor.execute(() -> {
            try {
                var gUser = googleAuthUtils.validateIdToken(code);
                if (Objects.isNull(gUser)) {
                    log.warn("Invalid Google Token");
                    return;
                }

                var user = userService.findOrCreateGoogleUser(gUser);
                userAuthDataRedisRepo.set(id, user, Duration.ofMinutes(2));
            } catch (Exception e) {
                log.error("Failed to find or create google user", e);
            }
        });

        return new AuthCookieResponse<>(signedUuid, "Processing token", null);
    }

    @Override
    public AuthCallbackResponse googleAuthCallback(String code, String state) {
        if (state != null && state.startsWith("redirect|")) {
            return processRedirectCallback(code, state);
        }

        if ("standard".equals(state)) {
            return processStandardCallback(code);
        }

        throw new UnauthorizedException("Invalid state");
    }

    private AuthCallbackResponse processRedirectCallback(String code, String state) {
        String[] parts = state.split("\\|");
        if (parts.length == 2) {
            String potentialTarget = parts[1];
            boolean isAllowed = mongoConfigService.getConfig().getFrontendUrls().stream()
                    .anyMatch(potentialTarget::equals);

            if (!isAllowed) {
                throw new BadRequestException("Unauthorized redirect origin");
            }

            String id = UUID.randomUUID().toString();
            String signedUuid = AuthUtil.signState(id, mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());

            taskExecutor.execute(() -> {
                var gUser = googleAuthUtils.googleCallbackProcessing(code, id);
                if (Objects.isNull(gUser)) {
                    return;
                }
                var user = userService.findOrCreateGoogleUser(gUser);
                userAuthDataRedisRepo.set(id, user, Duration.ofMinutes(2));
            });

            String targetURL = potentialTarget + "/google/callback?code=" + signedUuid + "&state=standard";
            return AuthCallbackResponse.redirect(targetURL);
        }
        throw new UnauthorizedException("Invalid state");
    }

    private AuthCallbackResponse processStandardCallback(String code) {
        String id = AuthUtil.extractAndVerify(code,
                mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());

        if (id == null) {
            throw new BadRequestException("Invalid or tampered session state");
        }

        User user = userAuthDataRedisRepo.get(id);
        if (user == null) {
            throw new NotFoundException("Request still under process or expired");
        }

        var userDto = user.toDto();
        String tokenStr = jwtService.generateToken(userDto);
        String cookie = sessionCookieIssuer.issue(tokenStr);
        authDataRedisRepo.set(String.valueOf(userDto.getUserId()), userDto, Duration.ofHours(1));
        userAuthDataRedisRepo.delete(id);

        return AuthCallbackResponse.session(cookie, userDto, "User created");
    }

}
