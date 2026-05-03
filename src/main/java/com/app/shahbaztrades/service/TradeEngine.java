package com.app.shahbaztrades.service;

import java.time.format.DateTimeFormatter;
import java.util.Set;

public interface TradeEngine {

    DateTimeFormatter HOUR_MIN_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    Set<String> FIFTEEN_MIN_TARGETS = Set.of(
            "09:35", "09:50", "10:05", "10:20", "10:35", "10:50",
            "11:05", "11:20", "11:35", "11:50", "12:05", "12:20",
            "12:35", "12:50", "13:05", "13:20", "13:35", "13:50",
            "14:05", "14:20", "14:35", "14:50", "15:05"
    );

    void continuousTrade();
}
