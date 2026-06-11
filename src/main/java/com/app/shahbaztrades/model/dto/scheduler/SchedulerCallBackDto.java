package com.app.shahbaztrades.model.dto.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;

import java.io.Serial;
import java.io.Serializable;

public record SchedulerCallBackDto(@NotBlank String url, @NotBlank String httpMethod,
                                   Object body,
                                   Object headers) implements Serializable {
    @Serial
    private static final long serialVersionUID = 3L;

    @JsonCreator
    public SchedulerCallBackDto {
    }
}
