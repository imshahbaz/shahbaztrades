package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.util.Cache;
import com.app.shahbaztrades.util.DateUtil;
import com.google.common.util.concurrent.Striped;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

/**
 * The bookkeeping behind watching open positions, for one kind of trade: which trades are live on
 * each token, the per-token lock guarding that list, and which trades already have an event in
 * flight.
 * <p>
 * Generic because the mechanics are identical for every trade type — only how you read a token and
 * an id off a trade differ, and those are supplied at construction. Adding a third kind of watched
 * trade is one more instance, not another copy of all of this.
 *
 * @param <T> the watched trade type
 */
public class WatchRegistry<T> {

    /** Fixed pool of locks so memory does not grow with the number of tokens seen. */
    private static final int LOCK_STRIPES = 8192;

    private final Cache<String, List<T>> watchCache = new Cache<>();
    private final Striped<Lock> tokenLocks = Striped.lock(LOCK_STRIPES);
    private final Set<String> triggered = ConcurrentHashMap.newKeySet();
    private final Function<T, String> tokenOf;
    private final Function<T, String> idOf;

    public WatchRegistry(Function<T, String> tokenOf, Function<T, String> idOf) {
        this.tokenOf = tokenOf;
        this.idOf = idOf;
    }

    /**
     * @return false when the session is already over, in which case the trade is not registered —
     * there will be no more ticks to act on.
     */
    public boolean watch(T trade) {
        if (DateUtil.isSquareOffTimeReached()) {
            return false;
        }

        String token = tokenOf.apply(trade);
        Lock lock = tokenLocks.get(token);
        lock.lock();
        try {
            List<T> trades = watchCache.get(token);
            if (trades == null) {
                trades = new CopyOnWriteArrayList<>();
                trades.add(trade);
                // The whole token entry expires at the close, so nothing outlives the session.
                watchCache.set(token, trades, DateUtil.getDurationUntilMarketClose());
            } else {
                trades.add(trade);
            }
        } finally {
            lock.unlock();
        }

        return true;
    }

    public void unwatch(T trade) {
        String token = tokenOf.apply(trade);
        Lock lock = tokenLocks.get(token);
        lock.lock();
        try {
            List<T> trades = watchCache.get(token);
            if (CollectionUtils.isEmpty(trades)) {
                return;
            }
            trades.remove(trade);
        } finally {
            lock.unlock();
        }
    }

    /** @return the trades live on this token, empty when there are none. Safe to iterate. */
    public List<T> watching(String token) {
        List<T> trades = watchCache.get(token);
        return trades == null ? List.of() : trades;
    }

    /**
     * Claims a trade so only one tick raises an event for it.
     *
     * @return true if this caller took the claim; false if an event is already in flight.
     */
    public boolean claim(T trade) {
        return triggered.add(idOf.apply(trade));
    }

    /** Releases the claim once the event has been handled, so later ticks can raise another. */
    public void release(T trade) {
        triggered.remove(idOf.apply(trade));
    }

    public int watchedTokenCount() {
        return watchCache.getActiveKeys().size();
    }

    public int watchedTradeCount() {
        int count = 0;
        for (String token : watchCache.getActiveKeys()) {
            List<T> trades = watchCache.get(token);
            if (trades != null) {
                count += trades.size();
            }
        }
        return count;
    }

    public int inFlightCount() {
        return triggered.size();
    }

    /** @return true if anything was actually discarded, so the caller can log it once. */
    public boolean purge() {
        if (watchCache.getActiveKeys().isEmpty()) {
            return false;
        }
        watchCache.invalidateAll();
        triggered.clear();
        return true;
    }
}
