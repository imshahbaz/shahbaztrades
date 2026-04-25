package com.app.shahbaztrades.model.dto.fcm;

import lombok.Builder;

import java.util.Map;

@Builder
public record NotificationRequest(long userId, String title, String body, Map<String, String> data) {
}
