package com.app.shahbaztrades.model.dto.system;

public record PipelineStats(
        int ringBufferSize,
        int shardCount,
        long ringRemainingCapacity,
        long ringUsedSlots
) {
}
