package com.app.shahbaztrades.service;

/**
 * Refreshes stored holdings with current market prices and broker leverage.
 * <p>
 * Split from {@link HoldingsService} because this is a scheduled sweep across every user, not an
 * operation on one user's holdings, and no caller wants both.
 */
public interface PortfolioValuationService {

    /** Re-prices every user's holdings, for every broker they hold with. */
    void updatePortfolio();
}
