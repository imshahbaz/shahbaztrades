package com.shahbaz.trades.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String NULL_SESSION = "NULL_SESSION";

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String requestURI = request.getRequestURI();

        // Allow access to static resources, login, signup, and API endpoints
        if (requestURI.equals("/login") ||
                requestURI.equals("/signup") ||
                requestURI.equals("/") ||
                requestURI.equals("/strategies")) {

            HttpSession session = request.getSession(false);
            if (session != null &&
                    session.getAttribute(MongoIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME) == null
                    && session.getAttribute(NULL_SESSION) == null) {
                session.setMaxInactiveInterval(180);
                session.setAttribute(NULL_SESSION, Boolean.TRUE);
            }

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
