package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;

public record RuntimeStats(
        long uptimeMs,
        long startTimeEpochMs
) {
    public static RuntimeStats snapshot() {
        var r = ManagementFactory.getRuntimeMXBean();
        return new RuntimeStats(r.getUptime(), r.getStartTime());
    }
}
