package com.app.shahbaztrades.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum SchedulerTaskType {
    CRON("1Klik-Scheduler-Crons"),
    TASK("1Klik-Scheduler-Tasks");

    @Getter
    private final String value;
}
