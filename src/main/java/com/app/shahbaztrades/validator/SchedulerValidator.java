package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.redisson.executor.CronExpression;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)

public class SchedulerValidator {
    private static final Set<String> allowedHttpMethods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    public static void validateCronDto(CronTaskDto cronTaskDto) {
        if (cronTaskDto.getType() != SchedulerTaskType.CRON) {
            throw new BadRequestException("Invalid type of task");
        }

        if (!CronExpression.isValidExpression(cronTaskDto.getCronExpression())) {
            throw new BadRequestException("The provided cron expression '" + cronTaskDto.getCronExpression() + "' has invalid syntax.");
        }

        validateCallBackDto(cronTaskDto.getCallBack());
    }

    public static void validateScheduledTaskDto(ScheduledTaskDto scheduledTaskDto) {
        if (scheduledTaskDto.getType() != SchedulerTaskType.TASK) {
            throw new BadRequestException("Invalid type of task");
        }

        if (scheduledTaskDto.getExecutionTime() < System.currentTimeMillis() + Duration.ofSeconds(10).toMillis()) {
            throw new BadRequestException("Invalid execution time");
        }

        validateCallBackDto(scheduledTaskDto.getCallBack());
    }

    private static void validateCallBackDto(SchedulerCallBackDto schedulerCallBackDto) {
        if (!allowedHttpMethods.contains(schedulerCallBackDto.httpMethod())) {
            throw new BadRequestException("Invalid HTTP method");
        }

        if (!isValidUrl(schedulerCallBackDto.url())) {
            throw new BadRequestException("Invalid URL");
        }
    }

    public static boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

}
