package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;
import java.util.List;

// Per-collector cumulative GC count and pause time since JVM start.
public record GcStats(
        String name,
        long collectionCount,
        long collectionTimeMs
) {
    public static List<GcStats> snapshot() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> new GcStats(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()))
                .toList();
    }
}
