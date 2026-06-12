package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SchedulerService {

    ResponseEntity<ApiResponse<String>> scheduleTask(ScheduledTaskDto scheduledTaskDto);

    ResponseEntity<ApiResponse<String>> scheduleCron(CronTaskDto cronTaskDto);

    ResponseEntity<ApiResponse<Boolean>> deleteTask(String id, SchedulerTaskType taskType);

    ResponseEntity<ApiResponse<Object>> getTask(String id, SchedulerTaskType taskType);

    ResponseEntity<ApiResponse<List<Object>>> getAllTask(SchedulerTaskType taskType);
}
