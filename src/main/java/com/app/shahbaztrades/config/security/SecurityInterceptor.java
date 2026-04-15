package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.exceptions.ForbiddenException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.enums.UserRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class SecurityInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

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
            if ("auth_token".equals(c.getName())) {
                if (StringUtils.isNotEmpty(c.getValue())) {
                    var claims = jwtService.validateToken(c.getValue());
                    userDto = claims.getUser();
                    request.setAttribute("user", claims.getUser());
                    break;
                }
            }
        }

        if (userDto == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        if (handlerMethod.hasMethodAnnotation(AdminOnly.class)) {
            if (!userDto.getRole().equals(UserRole.ADMIN)) {
                throw new ForbiddenException("Forbidden: Admin access required");
            }
        }

        return true;
    }
}