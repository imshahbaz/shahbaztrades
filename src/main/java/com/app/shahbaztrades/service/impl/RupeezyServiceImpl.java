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
import com.app.shahbaztrades.repo.redis.RupeezyTokenCacheRedisRepo;
import com.app.shahbaztrades.service.RupeezyService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Service
@RequiredArgsConstructor
public class RupeezyServiceImpl implements RupeezyService {

    private final RupeezyClient rupeezyClient;
    private final UserService userService;
    private final MongoTemplate mongoTemplate;
    private final RupeezyTokenCacheRedisRepo<RupeezyTokenCache> rupeezyTokenCacheRedisRepo;

    @Override
    public void login(BrokerLoginDto request) {
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
        rupeezyTokenCacheRedisRepo.set(String.valueOf(request.userId()), cache, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
    }

    @Override
    public ApiResponse<String> getAuth(UserDto userDto) {
        var user = getUser(userDto.getUserId());

        var config = user.getRupeezyConfig();
        if (!BrokerConfigValidator.validateRupeezyConfig(config)) {
            throw new NotFoundException("E001");
        }

        var cache = getTokenCache(user.getUserId());
        if (cache == null) {
            return ApiResponse.<String>builder()
                    .success(Boolean.FALSE)
                    .data(config.getAppId())
                    .message("Token expired")
                    .build();
        }

        try {
            var res = rupeezyClient.getUserFunds(config.getApiSecret(), BEARER_PREFIX + cache.getAccessToken());
            if (res.isEmpty() || res.get("nse") == null) {
                throw new UnauthorizedException("Access token expired");
            }
        } catch (Exception _) {
            return ApiResponse.<String>builder()
                    .success(Boolean.FALSE)
                    .data(config.getAppId())
                    .message("Token expired")
                    .build();
        }

        return ApiResponse.ok(String.valueOf(user.getUserId()), "Token already exist");
    }

    @Override
    public Long setConfig(User.RupeezyConfig config, UserDto userDto) {
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

        return userDto.getUserId();
    }

    @Override
    public RupeezyTokenCache getTokenCache(long userId) {
        var cache = rupeezyTokenCache.get(userId);
        if (cache == null) {
            cache = rupeezyTokenCacheRedisRepo.get(String.valueOf(userId));
            if (cache != null) {
                rupeezyTokenCache.set(userId, cache, Duration.ofSeconds(DateUtil.zerodhaTokenExpiry()));
            }
        }
        return cache;
    }

    @Override
    public void revokeRupeezyAuth(long userId) {
        rupeezyTokenCacheRedisRepo.delete(String.valueOf(userId));
        rupeezyTokenCache.remove(userId);
    }

    private User getUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        return user;
    }
}
