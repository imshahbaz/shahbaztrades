package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a broker's leverage shapes candidate selection. Brokers quote different margin figures and
 * differ on whether leverage should decide which candidate is taken first, so both live here rather
 * than as {@code if (broker == …)} branches in the trade engine.
 */
public interface BrokerMarginPolicy {

    BrokerType getBrokerType();

    /** The leverage multiplier this broker grants on the stock, or null when it will not fund it. */
    BigDecimal leverageFor(Margin margin);

    /**
     * Orders candidates by preference, most preferred first. Must tolerate candidates the broker
     * will not fund rather than throwing, since the caller treats any exception as "no trade".
     */
    List<Margin> rankCandidates(List<Margin> candidates);
}
