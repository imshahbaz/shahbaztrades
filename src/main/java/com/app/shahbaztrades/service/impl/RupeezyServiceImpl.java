package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.rupeezy.RupeezyClient;
import com.app.shahbaztrades.components.rupeezy.RupeezyTokenStore;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezySessionRequest;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.BrokerAuthService;
import com.app.shahbaztrades.service.RupeezyService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.validator.BrokerConfigValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;


import static com.app.shahbaztrades.util.Constants.BEARER_PREFIX;

@Service
@RequiredArgsConstructor
public class RupeezyServiceImpl implements RupeezyService, BrokerAuthService {

    private final RupeezyClient rupeezyClient;
    private final UserService userService;
    private final RupeezyTokenStore rupeezyTokenStore;

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
        rupeezyTokenStore.save(user.getUserId(), cache);
    }

    @Override
    public ApiResponse<String> getAuth(UserDto userDto) {
        var user = getUser(userDto.getUserId());

        var config = user.getRupeezyConfig();
        if (!BrokerConfigValidator.validateRupeezyConfig(config)) {
            throw new NotFoundException("E001");
        }

        var cache = rupeezyTokenStore.find(user.getUserId());
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

        if (!userService.updateRupeezyConfig(userDto.getUserId(), config)) {
            throw new UnauthorizedException("User not found");
        }

        return userDto.getUserId();
    }

    @Override
    public BrokerType getBrokerType() {
        return BrokerType.RUPEEZY;
    }

    @Override
    public void revokeAuth(long userId) {
        rupeezyTokenStore.delete(userId);
    }

    /** Rupeezy has no unattended login flow; the user must complete it themselves. */
    @Override
    public boolean supportsAutoLogin() {
        return false;
    }

    private User getUser(Long userId) {
        User user = userService.findByUserIdOrEmailOrMobile(userId, "", 0L);

        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        return user;
    }
}
