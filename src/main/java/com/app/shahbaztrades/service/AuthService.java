package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public interface AuthService {

    String logout();

    UserDto getMe(UserDto dto);

    ResponseEntity<ApiResponse<UserDto>> login(AuthRequest request, HttpServletResponse servletResponse);
}
