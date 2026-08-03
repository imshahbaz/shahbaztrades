package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;
import java.util.List;

public record ServerStats(
        MemoryStats memory,
        CpuStats cpu,
        ThreadStats threads,
        RuntimeStats runtime,
        FileDescriptorStats fileDescriptors,
        List<GcStats> gc,
        List<MemoryPoolStats> memoryPools,
        List<BufferPoolStats> bufferPools,
        DomainStats domain
) {
    public static ServerStats snapshot(DomainStats domain) {
        var memoryBean = ManagementFactory.getMemoryMXBean();
        return new ServerStats(
                MemoryStats.snapshot(memoryBean.getHeapMemoryUsage(), memoryBean.getNonHeapMemoryUsage()),
                CpuStats.snapshot(),
                ThreadStats.snapshot(),
                RuntimeStats.snapshot(),
                FileDescriptorStats.snapshot(),
                GcStats.snapshot(),
                MemoryPoolStats.snapshot(),
                BufferPoolStats.snapshot(),
                domain
        );
    }
}
