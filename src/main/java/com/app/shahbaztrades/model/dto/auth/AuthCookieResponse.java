package com.app.shahbaztrades.model.dto.auth;

/**
 * Transport-neutral result for auth flows that return a body plus (optionally) an auth cookie.
 * The controller decides the HTTP status/headers; the service stays free of {@code ResponseEntity}.
 *
 * @param cookie a {@code Set-Cookie} value to attach, or {@code null} when no cookie should be set.
 */
public record AuthCookieResponse<T>(T data, String message, String cookie) {
}
