package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;

// Per-pool breakdown (Eden, Old Gen, Metaspace, code cache, ...).
public record MemoryPoolStats(
        String name,
        long usedMb,
        long committedMb,
        long maxMb
) {
    private static final long MB = 1024L * 1024L;

    public static List<MemoryPoolStats> snapshot() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .map(p -> {
                    MemoryUsage u = p.getUsage();
                    long max = u.getMax();
                    return new MemoryPoolStats(p.getName(), u.getUsed() / MB, u.getCommitted() / MB, max > 0 ? max / MB : -1);
                })
                .toList();
    }
}
