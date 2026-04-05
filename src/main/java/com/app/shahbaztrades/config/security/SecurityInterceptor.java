package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.exceptions.UnauthorizedException;
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

        for (Cookie c : cookies) {
            if ("auth_token".equals(c.getName())) {
                if (StringUtils.isNotEmpty(c.getValue())) {
                    var claims = jwtService.validateToken(c.getValue());
                    request.setAttribute("user", claims.getUser());
                    return true;
                }
            }
        }

        throw new UnauthorizedException("Unauthorized");
    }
}