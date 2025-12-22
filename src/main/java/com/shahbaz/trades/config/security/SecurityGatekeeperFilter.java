package com.shahbaz.trades.config.security;

import com.shahbaz.trades.config.env.SystemConfigs;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityGatekeeperFilter extends OncePerRequestFilter {

    private final SystemConfigs systemConfigs;
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final AntPathMatcher matcher = new AntPathMatcher();

    private static final List<String> PUBLIC_PATTERNS = Arrays.asList(
            "/",
            "/login",
            "/signup",
            "/verify-otp",
            "/calculator",
            "/strategies",
            "/health",
            "/css/**",
            "/js/**",
            "/images/**",
            "/icons/**",
            "/webjars/**",
            "/webfonts/**",
            "/manifest.json",
            "/service-worker.js",
            "/favicon.ico",
            "/apple-touch-icon.png",
            "/favicon-32x32.png",
            "/favicon-16x16.png",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/.well-known/**",
            "static/**"
    );

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. GLOBAL CACHE CONTROL (Always apply)
        if (path.endsWith("service-worker.js")) {
            applyNoCacheHeaders(response);
        } else if (!isStaticResource(path)) {
            applyNoCacheHeaders(response);
        }

        // 2. EXCLUSION CHECK (Public & Static Resources)
        // If it's a public page or a CSS/JS file, let it pass immediately.
        boolean isPublic = PUBLIC_PATTERNS.stream()
                .anyMatch(pattern -> matcher.match(pattern, path));

        if (isPublic) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. API SECURITY (Handles /api/* paths)

        if (path.startsWith("/api/")) {
            String referer = request.getHeader("Referer");
            // Check if the request is coming from your own JSP pages
            boolean isInternal = referer != null && (referer.contains("/strategies") ||
                    referer.endsWith("/") ||
                    referer.contains("/admin/dashboard"));

            if (!isInternal) {
                String apiKey = request.getHeader(API_KEY_HEADER);
                if (!systemConfigs.getConfig().getApiKey().equals(apiKey)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("Unauthorized: API key is required");
                    return; // STOP HERE
                }
            }
            // If it's internal or has a valid key, let it pass to the API controller
            filterChain.doFilter(request, response);
            return; // IMPORTANT: Exit the filter here so it doesn't reach the Session check
        }

        // 4. SESSION AUTH (For all other private JSP pages)
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            filterChain.doFilter(request, response);
        } else {
            // Only redirect if it's a page request, not an API request
            response.sendRedirect(request.getContextPath() + "/login");
        }
    }

    private boolean isStaticResource(String path) {
        // Check by common extensions
        if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".json") ||
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".svg") || path.endsWith(".ico") ||
                path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                path.endsWith(".eot")) {
            return true;
        }

        // Check by directory (useful for libraries)
        return path.contains("/webjars/") || path.contains("/webfonts/") ||
                path.contains("/images/") || path.contains("/icons/") ||
                path.contains("/appicons/") || path.contains("/css/") ||
                path.contains("/favicon/");
    }

    private void applyNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }

}