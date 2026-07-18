package com.app.shahbaztrades.service.impl;

import org.junit.jupiter.api.Test;

import static com.app.shahbaztrades.service.impl.OrderServiceImpl.StopLossAction;
import static com.app.shahbaztrades.service.impl.OrderServiceImpl.decideStopLossAction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the pure stop-loss/ATR decision logic extracted from OrderServiceImpl.addStopLoss. */
class StopLossDecisionTest {

    private static final boolean NO_EXIT = true;
    private static final boolean HAS_EXIT = false;
    private static final boolean CLOSING = true;
    private static final boolean OPEN = false;

    @Test
    void noProfitYet_holdsEvenIfPriceDropped() {
        // Price fell below the peak-drop threshold, but profit was never reached -> do nothing.
        assertEquals(StopLossAction.NONE,
                decideStopLossAction(98.0, 100.0, 100.0, null, NO_EXIT, OPEN));
    }

    @Test
    void inProfitNoExitOrder_placesStopLoss() {
        // +1% and no protective order yet -> place the stop-loss.
        assertEquals(StopLossAction.PLACE_STOP_LOSS,
                decideStopLossAction(101.0, 100.0, 101.0, null, NO_EXIT, OPEN));
    }

    @Test
    void inProfitAndDroppedFromPeak_squaresOff() {
        // Profit reached and price fell 0.6% below the peak (no ATR) -> square off.
        assertEquals(StopLossAction.SQUARE_OFF,
                decideStopLossAction(100.5, 100.0, 102.0, null, HAS_EXIT, OPEN));
    }

    @Test
    void inProfitAndMarketClosing_squaresOff() {
        // Profit reached and market is closing, even without a price drop -> square off.
        assertEquals(StopLossAction.SQUARE_OFF,
                decideStopLossAction(101.0, 100.0, 101.0, null, HAS_EXIT, CLOSING));
    }

    @Test
    void atrTrailing_squaresOffBelowFloor() {
        // floor = peak - 0.4*ATR = 105 - 4 = 101; ltp 100.5 <= 101 and in profit -> square off.
        assertEquals(StopLossAction.SQUARE_OFF,
                decideStopLossAction(100.5, 100.0, 105.0, 10.0, HAS_EXIT, OPEN));
    }

    @Test
    void atrTrailing_holdsAboveFloorWithExistingExit() {
        // floor = 101; ltp 104 is above it and an exit order already exists -> hold.
        assertEquals(StopLossAction.NONE,
                decideStopLossAction(104.0, 100.0, 105.0, 10.0, HAS_EXIT, OPEN));
    }

    @Test
    void flatPrice_holds() {
        assertEquals(StopLossAction.NONE,
                decideStopLossAction(100.0, 100.0, 100.0, null, NO_EXIT, OPEN));
    }
}
