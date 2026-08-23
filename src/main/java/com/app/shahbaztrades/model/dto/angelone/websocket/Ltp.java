package com.app.shahbaztrades.model.dto.angelone.websocket;

/**
 * Outcome of a live-price lookup.
 * <p>
 * Replaces the old {@code double} sentinels ({@code -2} = feed down, {@code -1} = no tick yet), which
 * let callers silently collapse "the socket is dead" into "this token has not traded yet" via a
 * {@code ltp <= 0} check. Exhaustive switching now forces every caller to answer both cases.
 */
public sealed interface Ltp {

    Ltp NOT_SUBSCRIBED = new NotSubscribed();
    Ltp FEED_DOWN = new FeedDown();

    static Ltp of(double price) {
        return new Price(price);
    }

    /** A price seen on the live feed. Always positive: the decoder discards non-positive ticks. */
    record Price(double value) implements Ltp {
    }

    /** The feed is up, but no tick has arrived for this token yet. Subscribing and waiting may help. */
    record NotSubscribed() implements Ltp {
    }

    /** The websocket is down, so no token can have a price. Subscribing and waiting will not help. */
    record FeedDown() implements Ltp {
    }
}
