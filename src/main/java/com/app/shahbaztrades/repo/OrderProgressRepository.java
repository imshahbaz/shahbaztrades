package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.Order;

/**
 * Persists the fields a strategy advances during the day, leaving the rest of the order alone.
 * <p>
 * A port rather than a {@code MongoTemplate}, so strategies depend on "record this progress" and
 * not on how or where an order is stored.
 */
public interface OrderProgressRepository {

    /**
     * Writes the entry, exit, ATR and status of an in-flight order. Failures are logged, not
     * thrown: losing a progress write must not abort the strategy mid-session.
     */
    void saveProgress(Order order);
}
