package com.app.shahbaztrades.components.scheduler;

import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

@Data
@NoArgsConstructor(force = true)
public class SchedulerTask implements Runnable, Serializable {

    @Autowired
    private transient ScheduledTaskExecutor scheduledTaskExecutor;

    private CronTaskDto cronTaskDto;
    private ScheduledTaskDto scheduledTaskDto;

    public SchedulerTask(CronTaskDto cronTaskDto) {
        this.cronTaskDto = cronTaskDto;
    }

    public SchedulerTask(ScheduledTaskDto scheduledTaskDto) {
        this.scheduledTaskDto = scheduledTaskDto;
    }

    @Override
    public void run() {
        if (cronTaskDto != null) {
            scheduledTaskExecutor.executeCron(cronTaskDto);
        }

        if (scheduledTaskDto != null) {
            scheduledTaskExecutor.executeTask(scheduledTaskDto);
        }
    }
}
