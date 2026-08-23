package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.auth.AuthCallbackResponse;
import com.app.shahbaztrades.model.dto.auth.AuthCookieResponse;

/**
 * Sign-in through Google, across both the native ID-token flow and the browser redirect flow.
 * Split from {@link AuthService}, which handles our own sessions and password login.
 */
public interface GoogleAuthService {

    /** @param nativeFlow true when the caller already holds an ID token, rather than an auth code. */
    AuthCookieResponse<String> validateGoogleToken(String code, boolean nativeFlow);

    AuthCallbackResponse googleAuthCallback(String code, String state);
}
