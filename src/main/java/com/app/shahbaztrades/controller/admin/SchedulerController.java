package com.app.shahbaztrades.controller.admin;

import com.app.shahbaztrades.config.security.AdminOnly;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.service.SchedulerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/schedule")
public class SchedulerController {

    private final SchedulerService schedulerService;

    @PostMapping
    @AdminOnly
    public ResponseEntity<ApiResponse<String>> scheduleTask(@RequestBody @Valid ScheduledTaskDto scheduledTaskDto) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.scheduleTask(scheduledTaskDto), "Task scheduled successfully"));
    }

    @AdminOnly
    @DeleteMapping
    public ResponseEntity<ApiResponse<Boolean>> deleteTask(@RequestParam @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.deleteTask(id, SchedulerTaskType.TASK), "Task has been cancelled"));
    }

    @AdminOnly
    @PostMapping("/cron")
    public ResponseEntity<ApiResponse<String>> scheduleCron(@RequestBody @Valid CronTaskDto cronTaskDto) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.scheduleCron(cronTaskDto), "Cron scheduled successfully"));
    }

    @AdminOnly
    @DeleteMapping("/cron")
    public ResponseEntity<ApiResponse<Boolean>> deleteCron(@RequestParam @NotBlank String id) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.deleteTask(id, SchedulerTaskType.CRON), "Task has been cancelled"));
    }

    @AdminOnly
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Object>>> getAllTasks(@RequestParam @NotNull SchedulerTaskType taskType) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.getAllTask(taskType), "All tasks fetched successfully"));
    }

    @AdminOnly
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getTaskById(@PathVariable("id") @NotBlank String id, @RequestParam @NotNull SchedulerTaskType taskType) {
        return ResponseEntity.ok(ApiResponse.ok(schedulerService.getTask(id, taskType), "Task fetched successfully"));
    }

}
