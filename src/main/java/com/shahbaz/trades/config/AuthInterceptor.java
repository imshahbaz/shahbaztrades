package com.shahbaz.trades.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        // Allow access to static resources, login, signup, and API endpoints
        if (requestURI.equals("/login") ||
                requestURI.equals("/signup") ||
                requestURI.equals("/") ||
                requestURI.equals("/strategies") ||
                requestURI.equals("/verify-otp") ||
                requestURI.equals("/calculator")) {
            return true;
        }

        // Check if user is logged in via session
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return true;
        }

        // Redirect to login page
        response.sendRedirect("/login");
        return false;
    }
}
