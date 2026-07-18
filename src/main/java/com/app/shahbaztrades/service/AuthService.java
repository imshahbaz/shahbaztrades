package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCookieResponse;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.SignUpResponse;

public interface AuthService {

    String AUTH_KEY = "auth:";

    SignUpResponse signUp(AuthRequest request);

    String logout();

    UserDto getMe(UserDto dto);

    AuthCookieResponse<String> validateGoogleToken(String code, boolean nativeFlow);

    AuthCallbackResponse googleAuthCallback(String code, String state);
}
