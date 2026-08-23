package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.util.PriceUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Works out the exit price a continuous trade must reach to clear its costs and hit its target.
 * <p>
 * Injected rather than static because these figures are a commercial arrangement, not a law of the
 * market: brokerage, GST and the target percentage all change, and a backtest wants to vary them.
 */
@Component
public class TargetPricePolicy {

    private static final BigDecimal TAX_PER_SHARE = new BigDecimal("0.00035");
    private static final BigDecimal FIXED_BROKERAGE_WITH_GST = new BigDecimal("47.2");
    private static final BigDecimal TARGET_PERCENTAGE = new BigDecimal("0.007");
    private static final int PER_SHARE_SCALE = 6;

    /**
     * @param capitalDeployed the order's capital, which the target percentage is taken against
     * @param quantity        shares bought, used to spread the flat brokerage across the position
     * @return a tick-aligned sell price covering entry, taxes, brokerage and the target
     */
    public double targetFor(BigDecimal capitalDeployed, BigDecimal entryPrice, int quantity) {
        var targetOnCapital = capitalDeployed.multiply(TARGET_PERCENTAGE);
        var totalFixedBurden = targetOnCapital.add(FIXED_BROKERAGE_WITH_GST);
        var taxPerShare = entryPrice.multiply(TAX_PER_SHARE);
        var fixedBurdenPerShare = totalFixedBurden.divide(BigDecimal.valueOf(quantity), PER_SHARE_SCALE, RoundingMode.HALF_UP);
        var target = entryPrice.add(taxPerShare).add(fixedBurdenPerShare);
        return PriceUtil.fixToTick(target.doubleValue());
    }
}
