package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.zerodha.ZerodhaLoginDto;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.util.Cache;
import com.zerodhatech.kiteconnect.KiteConnect;
import org.springframework.http.ResponseEntity;

import java.util.Set;

public interface ZerodhaService {

    String ZERODHA_TOKEN_KEY = "zerodha_token_";

    Cache<Long, KiteConnect> kiteClientCache = new Cache<>();

    KiteConnect initiateKiteConnect(String accessToken, Long userId);

    String generateAccessToken(String requestToken, Long userId);

    KiteConnect getKiteClient(Long userId);

    ResponseEntity<ApiResponse<Void>> login(ZerodhaLoginDto request);

    ResponseEntity<ApiResponse<String>> getAuth(UserDto userDto);

    ResponseEntity<ApiResponse<Long>> setConfig(User.ZerodhaConfig config, UserDto userDto);

    void autoLogin(Set<Long> userIds);

    void autoConnectZerodhaSession(User user);
}
