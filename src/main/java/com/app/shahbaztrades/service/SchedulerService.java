package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import jakarta.validation.Valid;

import java.util.List;

public interface SchedulerService {

    String scheduleTask(ScheduledTaskDto scheduledTaskDto);

    String scheduleCron(CronTaskDto cronTaskDto);

    boolean deleteTask(String id, SchedulerTaskType taskType);

    Object getTask(String id, SchedulerTaskType taskType);

    List<Object> getAllTask(SchedulerTaskType taskType);

    String updateCron(String id, CronTaskDto cronTaskDto);
}
