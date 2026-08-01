package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;

// Platform threads only. Virtual threads (the @Async / EXECUTOR tasks) are NOT counted here.
public record ThreadStats(
        int live,
        int daemon,
        int peak,
        long totalStarted
) {
    public static ThreadStats snapshot() {
        var t = ManagementFactory.getThreadMXBean();
        return new ThreadStats(
                t.getThreadCount(),
                t.getDaemonThreadCount(),
                t.getPeakThreadCount(),
                t.getTotalStartedThreadCount()
        );
    }
}
