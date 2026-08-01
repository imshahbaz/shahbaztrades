package com.app.shahbaztrades.model.dto.system;

public record WatchdogStats(
        int watchedTokens,
        int watchedTrades,
        int mtfWatchedTokens,
        int mtfWatchedTrades,
        int inFlightTriggers,
        int inFlightMtfTriggers
) {
}
