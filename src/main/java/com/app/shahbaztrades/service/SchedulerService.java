package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;

import java.util.List;

public interface SchedulerService {

    String scheduleTask(ScheduledTaskDto scheduledTaskDto);

    String scheduleCron(CronTaskDto cronTaskDto);

    boolean deleteTask(String id, SchedulerTaskType taskType);

    Object getTask(String id, SchedulerTaskType taskType);

    List<Object> getAllTask(SchedulerTaskType taskType);
}
