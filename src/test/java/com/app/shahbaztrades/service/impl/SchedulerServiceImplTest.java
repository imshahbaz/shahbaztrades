package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.scheduler.SchedulerTask;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.SchedulerCallBackDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.CronSchedule;
import org.redisson.api.RMap;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceImplTest {

    private static final String EVERY_MINUTE = "0 * * * * ?";

    @Mock
    private RScheduledExecutorService scheduledExecutorService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RMap<Object, Object> registry;

    private SchedulerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerServiceImpl(scheduledExecutorService, redissonClient);
        lenient().when(redissonClient.getMap(anyString())).thenReturn(registry);
    }

    private SchedulerCallBackDto callback() {
        return new SchedulerCallBackDto("https://example.com/hook", "POST", null, Map.of());
    }

    private CronTaskDto cron(String id) {
        return new CronTaskDto(id, callback(), EVERY_MINUTE);
    }

    private ScheduledTaskDto task() {
        return new ScheduledTaskDto(callback(),
                System.currentTimeMillis() + Duration.ofMinutes(5).toMillis(), null);
    }

    // --- one-off tasks ----------------------------------------------------

    @Test
    void scheduleTask_generatesAnIdSchedulesAndRegistersTheTask() {
        String id = service.scheduleTask(task());

        assertNotNull(id);
        verify(scheduledExecutorService).schedule(eq(id), any(SchedulerTask.class), any(Duration.class));
        verify(registry).put(eq(id), any(ScheduledTaskDto.class));
    }

    @Test
    void scheduleTask_registersUnderTheTaskMapNotTheCronMap() {
        service.scheduleTask(task());
        verify(redissonClient).getMap(SchedulerTaskType.TASK.getValue());
    }

    @Test
    void scheduleTask_rejectsAnInvalidRequestBeforeTouchingRedis() {
        var expired = new ScheduledTaskDto(callback(), System.currentTimeMillis() - 1, null);

        assertThrows(BadRequestException.class, () -> service.scheduleTask(expired));
        verify(scheduledExecutorService, never()).schedule(anyString(), any(Runnable.class), any(Duration.class));
    }

    // --- crons ------------------------------------------------------------

    @Test
    void scheduleCron_schedulesAndRegistersWhenTheIdIsFree() {
        when(registry.get("cron-1")).thenReturn(null);

        assertEquals("cron-1", service.scheduleCron(cron("cron-1")));

        verify(scheduledExecutorService).schedule(eq("cron-1"), any(SchedulerTask.class), any(CronSchedule.class));
        verify(registry).put(eq("cron-1"), any(CronTaskDto.class));
    }

    @Test
    void scheduleCron_refusesToOverwriteAnExistingCron() {
        // Silently replacing a cron would leave the old chain running and double-fire callbacks.
        when(registry.get("cron-1")).thenReturn(cron("cron-1"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.scheduleCron(cron("cron-1")));
        verify(scheduledExecutorService, never())
                .schedule(anyString(), any(Runnable.class), any(CronSchedule.class));
    }

    @Test
    void scheduleCron_rejectsAnInvalidExpression() {
        assertThrows(BadRequestException.class,
                () -> service.scheduleCron(new CronTaskDto("cron-1", callback(), "nonsense")));
    }

    // --- lifecycle --------------------------------------------------------

    @Test
    void deleteTask_cancelsAndDeregisters() {
        when(scheduledExecutorService.cancelTask("t1")).thenReturn(Boolean.TRUE);

        assertTrue(service.deleteTask("t1", SchedulerTaskType.TASK));

        verify(registry).remove("t1");
    }

    @Test
    void deleteTask_throwsWhenNothingWasCancelled() {
        when(scheduledExecutorService.cancelTask("t1")).thenReturn(Boolean.FALSE);

        assertThrows(NotFoundException.class, () -> service.deleteTask("t1", SchedulerTaskType.TASK));
        verify(registry, never()).remove(anyString());
    }

    @Test
    void deleteTask_treatsANullCancelResultAsNotFound() {
        when(scheduledExecutorService.cancelTask("t1")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.deleteTask("t1", SchedulerTaskType.TASK));
    }

    @Test
    void getTask_returnsTheRegisteredDefinition() {
        var dto = cron("cron-1");
        when(registry.get("cron-1")).thenReturn(dto);

        assertEquals(dto, service.getTask("cron-1", SchedulerTaskType.CRON));
    }

    @Test
    void getTask_throwsForAnUnknownId() {
        when(registry.get("missing")).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.getTask("missing", SchedulerTaskType.CRON));
    }

    @Test
    void getAllTask_readsTheRegistryForTheRequestedType() {
        when(registry.values()).thenReturn(List.of(cron("a"), cron("b")));

        assertEquals(2, service.getAllTask(SchedulerTaskType.CRON).size());
        verify(redissonClient).getMap(SchedulerTaskType.CRON.getValue());
    }

    @Test
    void updateCron_cancelsTheOldChainBeforeSchedulingTheNewOne() {
        when(scheduledExecutorService.cancelTask("cron-1")).thenReturn(Boolean.TRUE);
        when(registry.get("cron-1")).thenReturn(null);

        assertEquals("cron-1", service.updateCron("cron-1", cron("cron-1")));

        verify(scheduledExecutorService).cancelTask("cron-1");
        verify(scheduledExecutorService).schedule(eq("cron-1"), any(SchedulerTask.class), any(CronSchedule.class));
    }

    @Test
    void updateCron_failsWithoutReschedulingWhenTheOldCronIsMissing() {
        when(scheduledExecutorService.cancelTask("cron-1")).thenReturn(Boolean.FALSE);

        assertThrows(NotFoundException.class, () -> service.updateCron("cron-1", cron("cron-1")));
        verify(scheduledExecutorService, never())
                .schedule(anyString(), any(Runnable.class), any(CronSchedule.class));
    }
}
