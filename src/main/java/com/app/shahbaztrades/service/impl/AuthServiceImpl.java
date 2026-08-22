package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.util.AuthUtil;
import com.app.shahbaztrades.components.auth.SessionCookieIssuer;
import com.app.shahbaztrades.config.security.JwtService;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.repo.redis.AuthDataRedisRepo;
import com.app.shahbaztrades.service.AuthService;
import com.app.shahbaztrades.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final SessionCookieIssuer sessionCookieIssuer;
    private final AuthDataRedisRepo<UserDto> authDataRedisRepo;

    @Override
    public String logout() {
        return sessionCookieIssuer.expire();
    }

    @Override
    public UserDto getMe(UserDto dto) {
        UserDto redisDto = authDataRedisRepo.get(String.valueOf(dto.getUserId()));
        if (redisDto != null) {
            return redisDto;
        }

        var user = userService.findByUserIdOrEmailOrMobile(dto.getUserId(), dto.getEmail(), dto.getMobile());
        if (Objects.isNull(user)) {
            throw new UnauthorizedException("User not found");
        }

        var newDto = user.toDto();
        authDataRedisRepo.set(String.valueOf(dto.getUserId()), newDto, Duration.ofHours(1));
        return newDto;
    }

    @Override
    public ResponseEntity<ApiResponse<UserDto>> login(AuthRequest request, HttpServletResponse servletResponse) {
        var user = userService.findByUserIdOrEmailOrMobile(0L, request.getEmail(), 0L);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        if (AuthUtil.ENCODER.matches(request.getPassword(), user.getPassword())) {
            var dto = user.toDto();
            var tokenStr = jwtService.generateToken(dto);
            var cookie = sessionCookieIssuer.issue(tokenStr);
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie);
            return ResponseEntity.ok(ApiResponse.ok(dto, "Login Success"));
        }

        throw new BadRequestException("Invalid credentials");
    }

}
