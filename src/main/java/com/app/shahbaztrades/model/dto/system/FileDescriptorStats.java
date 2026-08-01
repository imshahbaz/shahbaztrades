package com.app.shahbaztrades.model.dto.system;

import java.lang.management.ManagementFactory;

public record FileDescriptorStats(
        long open,
        long max
) {
    public static FileDescriptorStats snapshot() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.UnixOperatingSystemMXBean unixOs) {
            return new FileDescriptorStats(unixOs.getOpenFileDescriptorCount(), unixOs.getMaxFileDescriptorCount());
        }
        return new FileDescriptorStats(-1, -1);
    }
}
