package com.app.shahbaztrades.model.dto.system;

// App-domain signals: tick pipeline, watchdog load, websocket health.
public record DomainStats(
        PipelineStats pipeline,
        WatchdogStats watchdog,
        WebSocketStats webSocket
) {
}
