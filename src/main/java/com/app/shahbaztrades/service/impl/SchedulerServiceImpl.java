package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.scheduler.SchedulerTask;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.service.SchedulerService;
import com.app.shahbaztrades.validator.SchedulerValidator;
import lombok.RequiredArgsConstructor;
import org.redisson.api.CronSchedule;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final RScheduledExecutorService scheduledExecutorService;
    private final RedissonClient redissonClient;

    @Override
    public ResponseEntity<ApiResponse<String>> scheduleTask(ScheduledTaskDto scheduledTaskDto) {
        SchedulerValidator.validateScheduledTaskDto(scheduledTaskDto);
        scheduledTaskDto.generateTaskId();
        var delay = scheduledTaskDto.getExecutionTime() - System.currentTimeMillis();
        scheduledExecutorService.schedule(scheduledTaskDto.getTaskId(), new SchedulerTask(scheduledTaskDto), Duration.ofMillis(delay));
        var rMap = redissonClient.getMap(scheduledTaskDto.getType().getValue());
        rMap.put(scheduledTaskDto.getTaskId(), scheduledTaskDto);
        return ResponseEntity.ok(ApiResponse.ok(scheduledTaskDto.getTaskId(), "Task scheduled successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<String>> scheduleCron(CronTaskDto cronTaskDto) {
        SchedulerValidator.validateCronDto(cronTaskDto);
        scheduledExecutorService.schedule(cronTaskDto.getCronId(), new SchedulerTask(cronTaskDto), CronSchedule.of(cronTaskDto.getCronExpression()));
        var rMap = redissonClient.getMap(cronTaskDto.getType().getValue());
        rMap.put(cronTaskDto.getCronId(), cronTaskDto);
        return ResponseEntity.ok(ApiResponse.ok(cronTaskDto.getCronId(), "Cron scheduled successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<Boolean>> deleteTask(String id, SchedulerTaskType taskType) {
        Boolean wasCanceled = scheduledExecutorService.cancelTask(id);
        if (wasCanceled != null && wasCanceled) {
            var rMap = redissonClient.getMap(taskType.getValue());
            rMap.remove(id);
            return ResponseEntity.ok(ApiResponse.ok(true, "Task has been cancelled"));
        }

        throw new NotFoundException("Task not found");
    }

}
