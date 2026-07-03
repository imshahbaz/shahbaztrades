package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.exceptions.ForbiddenException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.util.HelperUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Objects;

import static com.app.shahbaztrades.util.Constants.ENV_PRODUCTION;

@Component
@RequiredArgsConstructor
public class SecurityInterceptor implements HandlerInterceptor {

    private static final long ROLLING_EXPIRY_THRESHOLD = Duration.ofHours(12).toMillis();

    private final JwtService jwtService;
    private final Environment environment;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class)) {
            return true;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        UserDto userDto = null;
        for (Cookie c : cookies) {
            if ("auth_token".equals(c.getName()) && StringUtils.isNotEmpty(c.getValue())) {
                var claims = jwtService.validateToken(c.getValue());
                userDto = claims.getUser();
                request.setAttribute("user", claims.getUser());
                rollToken(response, userDto, claims.getExpiresAt());
                break;
            }
        }

        if (userDto == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        if (handlerMethod.hasMethodAnnotation(AdminOnly.class) && !userDto.getRole().equals(UserRole.ADMIN)) {
            throw new ForbiddenException("Forbidden: Admin access required");
        }

        return true;
    }

    private void rollToken(@NonNull HttpServletResponse response, UserDto userDto, long previousExpiry) {
        var timeLeft = previousExpiry - System.currentTimeMillis();
        if (timeLeft < ROLLING_EXPIRY_THRESHOLD) {
            var tokenStr = jwtService.generateToken(userDto);
            var cookie = HelperUtil.createAuthCookie(tokenStr, 86400, Objects.equals(environment.getProperty("ENV"), ENV_PRODUCTION));
            response.addHeader(HttpHeaders.SET_COOKIE, cookie);
        }
    }

}