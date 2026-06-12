package com.app.shahbaztrades.model.dto.scheduler;

import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ScheduledTaskDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    private String taskId;

    private final SchedulerTaskType type = SchedulerTaskType.TASK;

    @Valid
    private final SchedulerCallBackDto callBack;

    @Min(1)
    private final long executionTime;

    public void generateTaskId() {
        this.taskId = UUID.randomUUID() + "-" + System.currentTimeMillis();
    }

}
