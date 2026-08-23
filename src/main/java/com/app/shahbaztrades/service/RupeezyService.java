package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;

/**
 * Rupeezy authentication for a user who is present. Token storage belongs to
 * {@link com.app.shahbaztrades.components.rupeezy.RupeezyTokenStore}; revoking is on
 * {@link BrokerAuthService}, which every broker implements.
 */
public interface RupeezyService {

    void login(BrokerLoginDto request);

    ApiResponse<String> getAuth(UserDto userDto);

    Long setConfig(User.RupeezyConfig config, UserDto userDto);

}
