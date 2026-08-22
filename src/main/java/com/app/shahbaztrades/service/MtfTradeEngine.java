package com.app.shahbaztrades.service;

/**
 * The daily MTF run, driven by the scheduler: place the orders, reconcile what the broker did with
 * them, then hand each one to its strategy for the session.
 * <p>
 * Split from {@link OrderService} because these are scheduled batch steps, not order CRUD, and no
 * caller ever wants both. The sibling of {@link TradeEngine}, which drives intraday continuous
 * trading rather than the daily MTF cycle.
 */
public interface MtfTradeEngine {

    /** Places today's pending orders as pre-market MTF orders. */
    void initiateMtfOrders();

    /** Reconciles today's placed orders against the broker's fill status. */
    void updateMtfOrderStatus();

    /** Hands each filled order to its strategy to manage for the session. */
    void startTrading();
}
