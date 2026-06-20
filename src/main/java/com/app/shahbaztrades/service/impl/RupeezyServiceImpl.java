package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.rupeezy.RupeezyClient;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezySessionRequest;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.service.RupeezyService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.util.HelperUtil;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RupeezyServiceImpl implements RupeezyService {

    private final RupeezyClient rupeezyClient;
    private final UserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MongoTemplate mongoTemplate;

    @Override
    public ResponseEntity<ApiResponse<Void>> login(BrokerLoginDto request) {
        var user = getUser(request.userId());
        var req = RupeezySessionRequest.builder()
                .applicationId(user.getRupeezyConfig().getAppId())
                .token(request.requestToken())
                .build();
        req.addChecksum(user.getRupeezyConfig().getApiSecret());
        var res = rupeezyClient.generateAccessToken(req);
        if (!res.isSuccess() || StringUtils.isEmpty(res.getData().getAccessToken())) {
            throw new NotFoundException("Access token not found");
        }

        var cache = RupeezyTokenCache.builder().apiSecret(user.getRupeezyConfig().getApiSecret())
                .accessToken(res.getData().getAccessToken()).build();
        rupeezyTokenCache.set(user.getUserId(), cache, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        stringRedisTemplate.opsForValue().set(RUPEEZY_TOKEN_KEY + request.userId(), HelperUtil.GSON.toJson(cache), Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
        return ResponseEntity.ok(ApiResponse.ok(null, "Flow invocation success"));
    }

    @Override
    public ResponseEntity<ApiResponse<String>> getAuth(UserDto userDto) {
        var user = getUser(userDto.getUserId());

        var config = user.getRupeezyConfig();
        if (!BrokerConfigValidator.validateRupeezyConfig(config)) {
            throw new NotFoundException("E001");
        }

        var cache = getTokenCache(user.getUserId());
        if (cache == null) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(Boolean.FALSE)
                    .data(config.getAppId())
                    .message("Token expired")
                    .build());
        }

        try {
            var res = rupeezyClient.getUserFunds(config.getApiSecret(), RupeezyClient.BEARER + cache.getAccessToken());
            if (res.isEmpty() || res.get("nse") == null) {
                throw new UnauthorizedException("Access token expired");
            }
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .success(Boolean.FALSE)
                    .data(config.getAppId())
                    .message("Token expired")
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.ok(String.valueOf(user.getUserId()), "Token already exist"));
    }

    @Override
    public ResponseEntity<ApiResponse<Long>> setConfig(User.RupeezyConfig config, UserDto userDto) {
        if (!BrokerConfigValidator.validateRupeezyConfig(config)) {
            throw new BadRequestException("Invalid request");
        }

        Query query = new Query(Criteria.where(User.Fields.userId).is(userDto.getUserId()));
        Update update = new Update();
        update.set(User.Fields.rupeezyConfig, config);
        var result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() < 1) {
            throw new UnauthorizedException("User not found");
        }

        return ResponseEntity.ok(ApiResponse.ok(userDto.getUserId(), "Rupeezy configuration updated successfully"));
    }

    @Override
    public RupeezyTokenCache getTokenCache(long userId) {
        var cache = rupeezyTokenCache.get(userId);
        if (cache == null) {
            var cacheString = stringRedisTemplate.opsForValue().get(RUPEEZY_TOKEN_KEY + userId);
            if (!StringUtils.isEmpty(cacheString)) {
                cache = HelperUtil.GSON.fromJson(cacheString, RupeezyTokenCache.class);
                rupeezyTokenCache.set(userId, cache, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
            }
        }
        return cache;
    }

    private User getUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        return user;
    }
}
