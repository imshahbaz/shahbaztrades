package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerValidatorTest {

    private static final String EVERY_MINUTE = "0 * * * * ?";

    private SchedulerCallBackDto callback(String url, String method) {
        return new SchedulerCallBackDto(url, method, null, Map.of());
    }

    private SchedulerCallBackDto validCallback() {
        return callback("https://example.com/hook", "POST");
    }

    @Test
    void validateCronDto_acceptsAValidExpressionAndCallback() {
        assertDoesNotThrow(() -> SchedulerValidator.validateCronDto(
                new CronTaskDto("cron-1", validCallback(), EVERY_MINUTE)));
    }

    @Test
    void validateCronDto_rejectsAMalformedCronExpression() {
        // A bad expression would otherwise blow up inside Redisson at schedule time.
        assertThrows(BadRequestException.class, () -> SchedulerValidator.validateCronDto(
                new CronTaskDto("cron-1", validCallback(), "not a cron")));
    }

    @Test
    void validateCronDto_rejectsAnUnsupportedHttpMethod() {
        assertThrows(BadRequestException.class, () -> SchedulerValidator.validateCronDto(
                new CronTaskDto("cron-1", callback("https://example.com", "TRACE"), EVERY_MINUTE)));
    }

    @Test
    void validateCronDto_rejectsANonHttpCallbackUrl() {
        // file:// or gopher:// callbacks would let a scheduled task reach the local filesystem.
        assertThrows(BadRequestException.class, () -> SchedulerValidator.validateCronDto(
                new CronTaskDto("cron-1", callback("file:///etc/passwd", "GET"), EVERY_MINUTE)));
    }

    @Test
    void validateScheduledTaskDto_acceptsAnExecutionTimeComfortablyInTheFuture() {
        long future = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
        assertDoesNotThrow(() -> SchedulerValidator.validateScheduledTaskDto(
                new ScheduledTaskDto(validCallback(), future, null)));
    }

    @Test
    void validateScheduledTaskDto_rejectsAPastExecutionTime() {
        long past = System.currentTimeMillis() - 1;
        assertThrows(BadRequestException.class, () -> SchedulerValidator.validateScheduledTaskDto(
                new ScheduledTaskDto(validCallback(), past, null)));
    }

    @Test
    void validateScheduledTaskDto_rejectsAnExecutionTimeInsideTheTenSecondGuardBand() {
        // Scheduling inside the guard band races the executor and can fire immediately or never.
        long tooSoon = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        assertThrows(BadRequestException.class, () -> SchedulerValidator.validateScheduledTaskDto(
                new ScheduledTaskDto(validCallback(), tooSoon, null)));
    }

    @Test
    void isValidUrl_acceptsHttpAndHttpsWithAHost() {
        assertTrue(SchedulerValidator.isValidUrl("http://example.com"));
        assertTrue(SchedulerValidator.isValidUrl("https://example.com/path?q=1"));
        assertTrue(SchedulerValidator.isValidUrl("HTTPS://EXAMPLE.COM"));
    }

    @Test
    void isValidUrl_rejectsBlankSchemelessAndHostlessValues() {
        assertFalse(SchedulerValidator.isValidUrl(null));
        assertFalse(SchedulerValidator.isValidUrl("   "));
        assertFalse(SchedulerValidator.isValidUrl("example.com"), "no scheme");
        assertFalse(SchedulerValidator.isValidUrl("https:///path"), "no host");
        assertFalse(SchedulerValidator.isValidUrl("ftp://example.com"));
        assertFalse(SchedulerValidator.isValidUrl(":::not a uri"));
    }

    @Test
    void scheduledTaskDto_generatesAUniqueTaskId() {
        var a = new ScheduledTaskDto(validCallback(), 1L, null);
        var b = new ScheduledTaskDto(validCallback(), 1L, null);
        a.generateTaskId();
        b.generateTaskId();

        assertFalse(a.getTaskId().equals(b.getTaskId()), "task ids key the Redis registry and must not collide");
    }
}
