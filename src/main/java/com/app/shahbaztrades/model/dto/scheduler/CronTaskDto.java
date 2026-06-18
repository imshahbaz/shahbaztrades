package com.app.shahbaztrades.model.dto.scheduler;

import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class CronTaskDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    private static final SchedulerTaskType TYPE = SchedulerTaskType.CRON;

    public SchedulerTaskType getType() {
        return TYPE;
    }

    @NotBlank
    private final String cronId;

    @Valid
    private final SchedulerCallBackDto callBack;

    @NotBlank
    private final String cronExpression;

}
