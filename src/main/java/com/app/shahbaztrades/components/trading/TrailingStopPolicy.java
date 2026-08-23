package com.app.shahbaztrades.components.trading;

import com.app.shahbaztrades.util.PriceUtil;
import org.springframework.stereotype.Component;

/**
 * Decides when to protect or close an open MTF position, and at what price.
 * <p>
 * Pure: every input is a parameter, so the thresholds can be exercised directly rather than through
 * a broker and a live tick stream. All four multipliers live here — the stop price the executor
 * places is derived from the same activation threshold the decision uses, and they must not drift
 * apart.
 */
@Component
public class TrailingStopPolicy {

    /** Profit needed before trailing arms at all, and the level the protective stop is placed at. */
    private static final double PROFIT_ACTIVATION_MULTIPLIER = 1.004;
    /** Gain at which a resting stop-loss order is worth placing. */
    private static final double STOP_LOSS_TRIGGER_MULTIPLIER = 1.006;
    /** Give-back from the peak that closes the position when no ATR is known. */
    private static final double PEAK_DROP_SQUARE_OFF_MULTIPLIER = 0.994;
    /** How much of the ATR the price may retrace from the peak before closing. */
    private static final double ATR_TRAILING_MULTIPLIER = 0.4;

    /**
     * @param atrValue       volatility-scaled trail, or null to fall back to a flat percentage
     * @param hasNoExitOrder true when nothing is protecting the position yet
     * @param marketClosing  true past the closing grace period, when positions must not be carried
     */
    public StopLossAction decide(double ltp, double buyPrice, double peakPrice,
                                 Double atrValue, boolean hasNoExitOrder, boolean marketClosing) {
        boolean reachedProfitThreshold = ltp > buyPrice * PROFIT_ACTIVATION_MULTIPLIER;

        boolean squareOff;
        if (atrValue != null) {
            double stopLossFloor = peakPrice - (atrValue * ATR_TRAILING_MULTIPLIER);
            squareOff = ltp <= stopLossFloor;
        } else {
            squareOff = ltp <= peakPrice * PEAK_DROP_SQUARE_OFF_MULTIPLIER;
        }

        // Only ever give back profit, never book a loss on the trail.
        if (reachedProfitThreshold && (squareOff || marketClosing)) {
            return StopLossAction.SQUARE_OFF;
        }

        if (hasNoExitOrder && ltp >= buyPrice * STOP_LOSS_TRIGGER_MULTIPLIER) {
            return StopLossAction.PLACE_STOP_LOSS;
        }

        return StopLossAction.NONE;
    }

    /** Price for the resting stop, tick-aligned so the broker will accept it. */
    public double stopLossPrice(double buyPrice) {
        return PriceUtil.fixToTick(buyPrice * PROFIT_ACTIVATION_MULTIPLIER);
    }

    public enum StopLossAction {NONE, SQUARE_OFF, PLACE_STOP_LOSS}
}
