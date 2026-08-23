package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.BrokerLoginDto;
import com.app.shahbaztrades.model.entity.User;

/**
 * Zerodha authentication for a user who is present: exchanging a request token, reporting whether
 * the session is still good, and storing their API credentials.
 * <p>
 * Building broker clients belongs to
 * {@link com.app.shahbaztrades.components.zerodha.ZerodhaClientFactory}; logging users in
 * unattended belongs to {@link ZerodhaAutoLoginService}.
 */
public interface ZerodhaService {

    /** Exchanges a request token for an access token and stores it for the trading day. */
    void login(BrokerLoginDto request);

    /** Reports whether the stored session still authenticates, for the login screen. */
    ApiResponse<String> getAuth(UserDto userDto);

    Long setConfig(User.ZerodhaConfig config, UserDto userDto);
}
