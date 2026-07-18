package com.app.shahbaztrades.controller;

import org.springframework.validation.annotation.Validated;

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
@Validated
public class SchedulerController {

    private final SchedulerService schedulerService;

    @PostMapping
    @PublicEndpoint
    public ResponseEntity<ApiResponse<String>> scheduleTask(@RequestBody @Valid ScheduledTaskDto scheduledTaskDto) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.scheduleTask(scheduledTaskDto), "Task scheduled successfully"));
    }

    @PublicEndpoint
    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> deleteTask(@RequestParam @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.deleteTask(id, SchedulerTaskType.TASK), "Task has been cancelled"));
    }

    @PublicEndpoint
    @PostMapping("/cron")
    public ResponseEntity<ApiResponse<String>> scheduleCron(@RequestBody @Valid CronTaskDto cronTaskDto) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.scheduleCron(cronTaskDto), "Cron scheduled successfully"));
    }

    @PublicEndpoint
    @DeleteMapping("/cron")
    public ResponseEntity<ApiResponse<Boolean>> deleteCron(@RequestParam @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.deleteTask(id, SchedulerTaskType.CRON), "Task has been cancelled"));
    }

    @PublicEndpoint
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Object>>> getAllTasks(@RequestParam @NotNull SchedulerTaskType taskType) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.getAllTask(taskType), "All tasks fetched successfully"));
    }

    @PublicEndpoint
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getTaskById(@PathParam("id") @NotBlank String id, @RequestParam @NotNull SchedulerTaskType taskType) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.getTask(id, taskType), "Task fetched successfully"));
    }

}
