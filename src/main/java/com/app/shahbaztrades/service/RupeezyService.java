package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.util.Cache;
import org.springframework.http.ResponseEntity;

public interface RupeezyService {

    String RUPEEZY_TOKEN_KEY = "rupeezy_token_";

    Cache<Long, RupeezyTokenCache> rupeezyTokenCache = new Cache<>();

    ResponseEntity<ApiResponse<Void>> login(BrokerLoginDto request);

    ResponseEntity<ApiResponse<String>> getAuth(UserDto userDto);

    ResponseEntity<ApiResponse<Long>> setConfig(User.RupeezyConfig config, UserDto userDto);

    RupeezyTokenCache getTokenCache(long userId);
}
