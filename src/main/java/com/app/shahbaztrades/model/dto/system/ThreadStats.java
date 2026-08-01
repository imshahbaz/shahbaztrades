package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;

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
