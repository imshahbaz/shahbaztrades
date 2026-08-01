package com.app.shahbaztrades.model.dto.system;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public record BufferPoolStats(
        String name,
        long count,
        long memoryUsedMb,
        long totalCapacityMb
) {
    private static final long MB = 1024L * 1024L;

    public static List<BufferPoolStats> snapshot() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .map(b -> new BufferPoolStats(b.getName(), b.getCount(), b.getMemoryUsed() / MB, b.getTotalCapacity() / MB))
                .toList();
    }
}
