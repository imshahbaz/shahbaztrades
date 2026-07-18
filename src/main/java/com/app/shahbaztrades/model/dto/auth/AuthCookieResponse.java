package com.app.shahbaztrades.model.dto.auth;

public record AuthCookieResponse<T>(T data, String message, String cookie) {
}
