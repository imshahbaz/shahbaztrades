package com.app.shahbaztrades.components.scheduler;

import com.app.shahbaztrades.model.dto.scheduler.CronTaskDto;
import com.app.shahbaztrades.model.dto.scheduler.ScheduledTaskDto;
import com.app.shahbaztrades.model.enums.SchedulerTaskType;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.CronSchedule;
import org.redisson.api.RMap;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskExecutor {

    private final RScheduledExecutorService scheduledExecutorService;
    private final RedissonClient redissonClient;

    public void executeTask(ScheduledTaskDto scheduledTask) {
        log.info("Executing scheduled task {}", scheduledTask.getTaskId());
        var res = HelperUtil.executeCallBack(scheduledTask.getCallBack());
        log.info("Executed scheduled task {} responseStatus {}", scheduledTask, res.getStatusCode());
        redissonClient.getMap(scheduledTask.getType().getValue()).remove(scheduledTask.getTaskId());
    }

    public void executeCron(CronTaskDto cronTaskDto) {
        log.info("Executing cron {}", cronTaskDto.getCronId());
        var res = HelperUtil.executeCallBack(cronTaskDto.getCallBack());
        log.info("Executed cron {} responseStatus {}", cronTaskDto, res.getStatusCode());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reviveCronOnStartup() {
        log.info("🔄 Scanning Redis registry to revive lost cron chains...");

        RMap<String, CronTaskDto> cronRegistry = redissonClient.getMap(SchedulerTaskType.CRON.getValue());

        if (cronRegistry.isEmpty()) {
            log.info("ℹ️ No cron found in the global registry map.");
            return;
        }

        cronRegistry.forEach((cronId, dto) -> {
            try {
                scheduledExecutorService.cancelTask(cronId);
                scheduledExecutorService.schedule(
                        cronId,
                        new SchedulerTask(dto),
                        CronSchedule.of(dto.getCronExpression())
                );

                log.info("✅ Successfully revived cron loop for ID: {}", cronId);
            } catch (Exception e) {
                log.error("❌ Failed to revive cron ID [{}]: ", cronId, e);
            }
        });
    }
}
