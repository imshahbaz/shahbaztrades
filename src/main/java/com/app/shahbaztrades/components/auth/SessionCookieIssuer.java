package com.app.shahbaztrades.components.auth;

import com.app.shahbaztrades.model.enums.Environments;
import com.app.shahbaztrades.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Builds the session cookie. One place decides its lifetime and whether it is marked Secure, so the
 * several login paths cannot drift apart on those.
 */
@Component
@RequiredArgsConstructor
public class SessionCookieIssuer {

    private static final int SESSION_MAX_AGE_SECONDS = 86400;

    private final Environment environment;

    public String issue(String token) {
        return AuthUtil.createAuthCookie(token, SESSION_MAX_AGE_SECONDS, isProduction());
    }

    /** A max-age of -1 tells the browser to drop the cookie immediately. */
    public String expire() {
        return AuthUtil.createAuthCookie("", -1, isProduction());
    }

    private boolean isProduction() {
        return Objects.equals(environment.getProperty("ENV"), Environments.PRODUCTION.name());
    }
}
