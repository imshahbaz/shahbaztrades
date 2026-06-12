package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.service.SchedulerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedule")
public class SchedulerController {

    private final SchedulerService schedulerService;

    @PostMapping
    @PublicEndpoint
    public ResponseEntity<ApiResponse<String>> scheduleTask(@RequestBody @Valid ScheduledTaskDto scheduledTaskDto) {
        return schedulerService.scheduleTask(scheduledTaskDto);
    }

    @PublicEndpoint
    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> deleteTask(@RequestParam @NotBlank String id) {
        return schedulerService.deleteTask(id, SchedulerTaskType.TASK);
    }

    @PublicEndpoint
    @PostMapping("/cron")
    public ResponseEntity<ApiResponse<String>> scheduleCron(@RequestBody @Valid CronTaskDto cronTaskDto) {
        return schedulerService.scheduleCron(cronTaskDto);
    }

    @PublicEndpoint
    @DeleteMapping("/cron")
    public ResponseEntity<ApiResponse<Boolean>> deleteCron(@RequestParam @NotBlank String id) {
        return schedulerService.deleteTask(id, SchedulerTaskType.CRON);
    }
}
