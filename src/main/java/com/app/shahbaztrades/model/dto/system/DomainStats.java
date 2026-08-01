package com.app.shahbaztrades.model.dto.system;

public record DomainStats(
        PipelineStats pipeline,
        WatchdogStats watchdog,
        WebSocketStats webSocket
) {
}
