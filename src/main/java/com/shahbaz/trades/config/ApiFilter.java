package com.shahbaz.trades.config;

import com.mongodb.lang.NonNull;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-KEY";

    @NonNull
    @Value("${API_KEY}")
    private String API_KEY;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/swagger-ui.html") || path.startsWith("/v3/api/docs")
                || path.startsWith("swagger-resources")
                || path.startsWith("/swagger-ui/index.html")
                || !path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String referer = request.getHeader("Referer");

        boolean isFromJsp = referer != null && (referer.endsWith("/") || referer.contains("/strategies"));

        if (!isFromJsp) {
            String apiKey = request.getHeader(HEADER_NAME);
            if (!API_KEY.equals(apiKey)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Unauthorized: API key is required");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

}
