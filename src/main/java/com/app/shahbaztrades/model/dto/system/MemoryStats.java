package com.app.shahbaztrades.model.dto.system;

import java.lang.management.MemoryUsage;

public record MemoryStats(
        long heapUsedMb,
        long heapCommittedMb,
        long heapMaxMb,
        int heapUsedPercent,
        long nonHeapUsedMb,
        long nonHeapCommittedMb
) {
    private static final long MB = 1024L * 1024L;

    public static MemoryStats snapshot(MemoryUsage heap, MemoryUsage nonHeap) {
        long heapMax = heap.getMax(); // -1 when undefined
        int usedPercent = heapMax > 0 ? (int) (heap.getUsed() * 100 / heapMax) : -1;
        return new MemoryStats(
                heap.getUsed() / MB,
                heap.getCommitted() / MB,
                heapMax > 0 ? heapMax / MB : -1,
                usedPercent,
                nonHeap.getUsed() / MB,
                nonHeap.getCommitted() / MB
        );
    }
}
