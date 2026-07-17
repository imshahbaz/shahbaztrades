package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.sessionmanager.SessionManagerClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginRequestDTO;
import com.app.shahbaztrades.model.dto.sessionmanager.ZerodhaLoginResponseDTO;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.service.ZerodhaService;
import com.app.shahbaztrades.util.Constants;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZerodhaServiceImpl implements ZerodhaService {

    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MongoTemplate mongoTemplate;
    private final SessionManagerClient sessionManagerClient;

    @Override
    public KiteConnect initiateKiteConnect(String accessToken, Long userId) {
        log.info("Initiating KiteConnect for User ID: {}", userId);

        User user = getUser(userId);

        if (!BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
            throw new NotFoundException("Zerodha API configuration is missing for this user");
        }

        KiteConnect kc = new KiteConnect(user.getZerodhaConfig().getApiKey());
        kc.setAccessToken(accessToken);
        return kc;
    }

    @Override
    public String generateAccessToken(String requestToken, Long userId) {
        log.info("Generating access token for User ID: {}", userId);

        User user = getUser(userId);

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

    @Override
    public KiteConnect getKiteClient(Long userId) {

        var cachedClient = kiteClientCache.get(userId);
        if (cachedClient != null) {
            return cachedClient;
        }

        String accessToken = stringRedisTemplate.opsForValue().get(ZERODHA_TOKEN_KEY + userId);
        if (StringUtils.isEmpty(accessToken)) {
            throw new NotFoundException("Access token not found in redis for user " + userId);
        }

        KiteConnect kc = initiateKiteConnect(accessToken, userId);

        kiteClientCache.set(userId, kc, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));

        return kc;
    }

    @Override
    @CacheEvict(value = "zerodhaAuthCache", key = "#request.userId")
    public ResponseEntity<ApiResponse<Void>> login(BrokerLoginDto request) {
        var token = generateAccessToken(request.requestToken(), request.userId());
        stringRedisTemplate.opsForValue().set(ZERODHA_TOKEN_KEY + request.userId(), token, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        kiteClientCache.remove(request.userId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Flow invocation success"));
    }

    @Override
    @Cacheable(value = "zerodhaAuthCache", key = "#userDto.userId", sync = true)
    public ApiResponse<String> getAuth(UserDto userDto) {
        var user = getUser(userDto.getUserId());

        if (!BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
            throw new NotFoundException("E001");
        }

        if (user.getZerodhaConfig().isTotpEnabled()) {
            var key = stringRedisTemplate.opsForValue().get(Constants.ZERODHA_AUTO_LOGIN_KEY + userDto.getUserId());
            if (!StringUtils.isEmpty(key)) {
                throw new ResourceAlreadyExistsException("E002");
            }
        }

        try {
            var kc = getKiteClient(userDto.getUserId());
            kc.getProfile();
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
    public ResponseEntity<ApiResponse<Long>> setConfig(User.ZerodhaConfig config, UserDto userDto) {
        if (!BrokerConfigValidator.validateZerodhaConfig(config)) {
            throw new BadRequestException("Invalid request");
        }

        if (!StringUtils.isEmpty(config.getUserName()) && StringUtils.isAnyEmpty(config.getPassword(), config.getTotpSecret())) {
            throw new BadRequestException("Invalid request");
        }

        Query query = new Query(Criteria.where(User.Fields.userId).is(userDto.getUserId()));
        Update update = new Update();
        update.set(User.Fields.zerodhaConfig, config);
        var result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() < 1) {
            throw new UnauthorizedException("User not found");
        }

        return ResponseEntity.ok(ApiResponse.ok(userDto.getUserId(), "Zerodha configuration updated successfully"));
    }

    @Override
    public void autoLogin(Set<Long> userIds) {
        var users = userService.findByIds(userIds);
        if (users.isEmpty()) {
            log.info("Users not found for zerodha auto login");
            return;
        }

        for (User user : users) {
            if (user.getZerodhaConfig() != null && user.getZerodhaConfig().isTotpEnabled()) {
                HelperUtil.EXECUTOR.execute(() -> {
                    try {
                        tryAutoLogin(user);
                    } catch (InterruptedException e) {
                        log.info("Auto login interrupted {}", user.getUserId(), e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.info("Auto login failed {} {}", user.getUserId(), e.getMessage());
                    }
                });
            }
        }
    }

    @Override
    public void autoConnectZerodhaSession(User user) {
        if (user.getZerodhaConfig() != null && user.getZerodhaConfig().isTotpEnabled() && BrokerConfigValidator.validateZerodhaConfig(user.getZerodhaConfig())) {
            var res = sessionManagerClient.autoLogin(ZerodhaLoginRequestDTO.mapDto(user.getUserId(), user.getZerodhaConfig()), SessionManagerClient.SOURCE);
            if (res.isPending() && res.message().equals("Token generation already in progress")) {
                throw new ResourceAlreadyExistsException("Request already exists");
            } else if (res.isError()) {
                log.error("Auto login failed at session manager{}", user.getUserId());
                stringRedisTemplate.delete(Constants.ZERODHA_AUTO_LOGIN_KEY + user.getUserId());
                throw new BadRequestException("Auto login failed at session manager");
            }
        } else {
            stringRedisTemplate.delete(Constants.ZERODHA_AUTO_LOGIN_KEY + user.getUserId());
            throw new BadRequestException("Auto login is not enabled");
        }
    }

    @Override
    @Async("taskExecutor")
    public void sessionManagerCallback(ZerodhaLoginResponseDTO request) {
        try {
            if (request.isError() || StringUtils.isEmpty(request.requestToken())) {
                return;
            }

            login(new BrokerLoginDto(request.requestToken(), request.userid()));
        } catch (Exception e) {
            log.error("Session Manager callback exception {}", request.requestToken(), e);
        } finally {
            stringRedisTemplate.delete(Constants.ZERODHA_AUTO_LOGIN_KEY + request.userid());
        }
    }

    private void tryAutoLogin(User user) throws InterruptedException {
        var userId = user.getUserId();
        try {
            var kc = getKiteClient(userId);
            kc.getProfile();
            return;
        } catch (NotFoundException _) {
            log.info("Access token not found proceeding with auto login {}", userId);
        } catch (IOException | KiteException _) {
            log.info("Kite connection failed proceeding with auto login {}", userId);
        }

        autoConnectZerodhaSession(user);
    }

    private User getUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        return user;
    }

}
