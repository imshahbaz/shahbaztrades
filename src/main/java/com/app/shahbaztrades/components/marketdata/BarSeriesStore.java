package com.app.shahbaztrades.components.marketdata;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The 15-minute bar series per token, and the per-token lock guarding each one.
 * <p>
 * The live series is never handed out: readers get {@link #snapshot}, so a consumer iterating bars
 * cannot race a tick appending to them.
 */
@Slf4j
@Component
public class BarSeriesStore {

    static final Duration BAR_PERIOD = Duration.ofMinutes(15);
    private static final int MAX_BARS = 200;

    private final ConcurrentHashMap<String, BarSeries> tokenSeriesMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tokenLocks = new ConcurrentHashMap<>();

    /** A detached copy of the token's bars, safe to read while ticks keep arriving. */
    public BarSeries snapshot(String token) {
        ReentrantLock lock = lockFor(token);
        lock.lock();
        try {
            BarSeries live = seriesFor(token);
            BarSeries copy = new BaseBarSeriesBuilder().withName(live.getName()).build();
            for (int i = live.getBeginIndex(); i <= live.getEndIndex(); i++) {
                copy.addBar(live.getBar(i));
            }
            return copy;
        } finally {
            lock.unlock();
        }
    }

    /** Seeds a series from history. Candle timestamps are bar starts, so each end is one period later. */
    public void appendHistory(String token, List<SmartApiLtpResponse.CandleDetail> candles) {
        BarSeries series = seriesFor(token);
        ReentrantLock lock = lockFor(token);
        lock.lock();
        try {
            for (var candle : candles) {
                series.addBar(buildBar(series, candle.timestamp().plus(BAR_PERIOD).toInstant(),
                        candle.open(), candle.high(), candle.low(), candle.close()), false);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Folds a tick into the bar ending at {@code barEndTime}, opening that bar if it is the first. */
    public void applyTick(String token, double ltp, Instant barEndTime) {
        BarSeries series = seriesFor(token);
        ReentrantLock lock = lockFor(token);
        lock.lock();
        try {
            if (series.isEmpty() || !series.getLastBar().getEndTime().equals(barEndTime)) {
                series.addBar(buildBar(series, barEndTime, ltp, ltp, ltp, ltp), false);
            } else {
                series.addPrice(ltp);
            }
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        tokenSeriesMap.clear();
        tokenLocks.clear();
    }

    private BarSeries seriesFor(String token) {
        return tokenSeriesMap.computeIfAbsent(token, name -> {
            BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
            series.setMaximumBarCount(MAX_BARS);
            return series;
        });
    }

    private ReentrantLock lockFor(String token) {
        return tokenLocks.computeIfAbsent(token, _ -> new ReentrantLock());
    }

    private Bar buildBar(BarSeries series, Instant endInstant, double o, double h, double l, double c) {
        return series.barBuilder()
                .timePeriod(BAR_PERIOD)
                .endTime(endInstant)
                .openPrice(o)
                .highPrice(h)
                .lowPrice(l)
                .closePrice(c)
                .volume(0L)
                .build();
    }
}
