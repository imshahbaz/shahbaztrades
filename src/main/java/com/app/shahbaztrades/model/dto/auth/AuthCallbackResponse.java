package com.app.shahbaztrades.model.dto.auth;

import com.app.shahbaztrades.model.dto.UserDto;

/**
 * Result of the Google OAuth callback, which is either a redirect (to a front-end origin) or a
 * completed session (auth cookie + user). Keeps the service free of {@code ResponseEntity} while
 * letting the controller render the right HTTP response.
 */
public record AuthCallbackResponse(String redirectUrl, String cookie, UserDto user, String message) {

    public static AuthCallbackResponse redirect(String url) {
        return new AuthCallbackResponse(url, null, null, null);
    }

    public static AuthCallbackResponse session(String cookie, UserDto user, String message) {
        return new AuthCallbackResponse(null, cookie, user, message);
    }

    public boolean isRedirect() {
        return redirectUrl != null;
    }
}
