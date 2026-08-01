package com.app.shahbaztrades.model.dto.system;

public record WebSocketStats(
        boolean connected,
        int reconnectAttempts
) {
}
