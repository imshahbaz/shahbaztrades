package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.SignUpResponse;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<ApiResponse<SignUpResponse>> signUp(AuthRequest request);

    ResponseEntity<ApiResponse<Void>> Logout();

    ResponseEntity<ApiResponse<UserDto>> getMe(UserDto dto);

    ResponseEntity<ApiResponse<String>> validateGoogleToken(String code);

    ResponseEntity<?> googleAuthCallback(String code, String state);
}
