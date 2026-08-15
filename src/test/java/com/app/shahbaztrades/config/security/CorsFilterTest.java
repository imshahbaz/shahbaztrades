package com.app.shahbaztrades.config.security;

import com.app.shahbaztrades.service.MongoConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorsFilterTest {

    private static final String ALLOWED = "https://app.example.com";

    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private CorsFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorsFilter(mongoConfigService);
        var config = new MongoEnvConfig();
        config.setFrontendUrls(List.of(ALLOWED));
        lenient().when(mongoConfigService.getConfig()).thenReturn(config);
    }

    @Test
    void allowedOrigin_getsCredentialedCorsHeadersAndPassesThrough() throws Exception {
        when(request.getHeader("Origin")).thenReturn(ALLOWED);
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Access-Control-Allow-Origin", ALLOWED);
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        // Without Vary: Origin a shared cache could serve one tenant's headers to another.
        verify(response).setHeader("Vary", "Origin");
        verify(chain).doFilter(request, response);
    }

    @Test
    void unknownOrigin_getsNoCorsHeadersButTheRequestStillProceeds() throws Exception {
        // Server-to-server and same-origin calls carry no usable Origin and must not be blocked here.
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingOriginHeader_isTreatedAsNotAllowed() throws Exception {
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void preflightFromAnAllowedOrigin_isAnsweredWith200AndNotForwarded() throws Exception {
        when(request.getHeader("Origin")).thenReturn(ALLOWED);
        when(request.getMethod()).thenReturn("OPTIONS");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void preflightFromAnUnknownOrigin_isRejectedWith403() throws Exception {
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");
        when(request.getMethod()).thenReturn("OPTIONS");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void preflightMethodMatchIsCaseInsensitive() throws Exception {
        when(request.getHeader("Origin")).thenReturn(ALLOWED);
        when(request.getMethod()).thenReturn("options");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(chain, never()).doFilter(request, response);
    }
}
