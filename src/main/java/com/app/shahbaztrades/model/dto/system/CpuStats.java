package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;

public record CpuStats(
        int processCpuPercent,
        int systemCpuPercent,
        double systemLoadAverage,
        int availableProcessors
) {
    public static CpuStats snapshot() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        int processPct = -1;
        int systemPct = -1;
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            processPct = toPercent(sunOs.getProcessCpuLoad());
            systemPct = toPercent(sunOs.getCpuLoad());
        }
        return new CpuStats(processPct, systemPct, os.getSystemLoadAverage(), os.getAvailableProcessors());
    }

    private static int toPercent(double load) {
        return load < 0 ? -1 : (int) Math.round(load * 100);
    }
}
