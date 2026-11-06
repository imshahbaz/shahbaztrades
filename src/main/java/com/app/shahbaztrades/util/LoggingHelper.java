package com.app.shahbaztrades.util;

import com.app.shahbaztrades.model.entity.Margin;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LoggingHelper {

    public static void logSignalsTable(String strategyName, List<Margin> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder(signals.size() * 45 + 128);
        sb.append(String.format("Strategy [%s] Signals (%d):%n", strategyName, signals.size()));
        sb.append(String.format("  %-12s | %-12s | %-12s%n", "SYMBOL", "ZERODHA", "RUPEEZY"));
        sb.append("  ----------------------------------------");

        for (Margin m : signals) {
            sb.append(String.format("%n  %-12s | %-12.2f | %-12.2f",
                    m.getSymbol(),
                    m.getRequiredMargin(),
                    m.getRupeezyMargin()));
        }

        log.info("{}", sb);
    }

}
