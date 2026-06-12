package com.app.shahbaztrades.controller;

import com.app.shahbaztrades.config.security.PublicEndpoint;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.service.SchedulerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PublicEndpoint
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Object>>> getAllTasks(@RequestParam @NotNull SchedulerTaskType taskType) {
        return schedulerService.getAllTask(taskType);
    }

    @PublicEndpoint
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getTaskById(@PathParam("id") @NotBlank String id, @RequestParam @NotNull SchedulerTaskType taskType) {
        return schedulerService.getTask(id, taskType);
    }

}
