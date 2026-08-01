package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;
import java.util.List;

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
