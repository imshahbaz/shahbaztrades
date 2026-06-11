package com.app.shahbaztrades.model.dto.scheduler;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.redisson.executor.CronExpression;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class CronTaskDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    private final SchedulerTaskType type = SchedulerTaskType.CRON;

    @NotBlank
    private final String cronId;

    @Valid
    private final SchedulerCallBackDto callBack;

    @NotBlank
    private final String cronExpression;

    public void validate() {
        if (this.type != SchedulerTaskType.CRON) {
            throw new BadRequestException("Invalid type of task");
        }

        if (!CronExpression.isValidExpression(this.cronExpression)) {
            throw new BadRequestException("The provided cron expression '" + cronExpression + "' has invalid syntax.");
        }
    }
}
