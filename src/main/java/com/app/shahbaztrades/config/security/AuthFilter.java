package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.util.HelperUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Environment environment;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Cookie authCookie = WebUtils.getCookie(request, "auth_token");

        if (authCookie == null || authCookie.getValue().isEmpty()) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String token = authCookie.getValue();
        var claims = jwtService.validateToken(token);

        if (claims.getExpiresAt() < System.currentTimeMillis() + Duration.ofMinutes(15).toMillis()) {
            String newToken = jwtService.generateToken(claims.getUser());
            String cookieString = HelperUtil.createAuthCookie(newToken, 86400, Objects.equals(environment.getProperty("ENV"), "production"));
            response.addHeader(HttpHeaders.SET_COOKIE, cookieString);
        }

        request.setAttribute("user", claims.getUser());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                claims.getUser(), null, new ArrayList<>());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return Arrays.stream(SecurityConfig.PUBLIC_URLS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, String> errorResponse = Map.of("error", message);
        response.getWriter().write(HelperUtil.GSON.toJson(errorResponse));
    }

}