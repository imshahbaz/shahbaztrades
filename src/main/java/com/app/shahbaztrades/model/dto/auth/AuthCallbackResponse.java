package com.app.shahbaztrades.model.dto.auth;

import com.app.shahbaztrades.model.dto.UserDto;

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
