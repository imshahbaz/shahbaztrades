package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.util.Cache;

public interface RupeezyService {

    Cache<Long, RupeezyTokenCache> rupeezyTokenCache = new Cache<>();

    void login(BrokerLoginDto request);

    ApiResponse<String> getAuth(UserDto userDto);

    Long setConfig(User.RupeezyConfig config, UserDto userDto);

    RupeezyTokenCache getTokenCache(long userId);

    void revokeRupeezyAuth(long userId);
}
