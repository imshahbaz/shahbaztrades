package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class MemoryMonitorController {

    @PublicEndpoint
    @GetMapping("/mem")
    public Map<String, Object> getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long mb = 1024 * 1024;

        return Map.of(
            "used_memory_mb", (runtime.totalMemory() - runtime.freeMemory()) / mb,
            "free_memory_mb", runtime.freeMemory() / mb,
            "total_memory_allocated_mb", runtime.totalMemory() / mb,
            "max_memory_limit_mb", runtime.maxMemory() / mb,
            "available_processors", runtime.availableProcessors()
        );
    }
}