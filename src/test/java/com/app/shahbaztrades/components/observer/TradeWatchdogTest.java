package com.app.shahbaztrades.components.observer;

import com.app.shahbaztrades.model.dto.order.ActiveMtfTrade;
import com.app.shahbaztrades.model.dto.order.MtfTickEvent;
import com.app.shahbaztrades.model.dto.strategy.ActiveTrade;
import com.app.shahbaztrades.model.dto.strategy.TradeCompletionEvent;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeWatchdogTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private TradeWatchdog watchdog;

    @BeforeEach
    void setUp() {
        watchdog = new TradeWatchdog(applicationEventPublisher);
        // watch() short-circuits after 15:30 IST, so the registration tests need a live session.
        Assumptions.assumeFalse(DateUtil.isSquareOffTimeReached(),
                "watchdog registration is disabled after square-off time");
    }

    private ActiveTrade trade(String orderId, double target) {
        return ActiveTrade.builder()
                .strategyOrderId(orderId).userId(7L).symbol("TCS").token("11536")
                .quantity(10).targetPrice(target).build();
    }

    private ActiveMtfTrade mtfTrade(String orderId, double peak) {
        return ActiveMtfTrade.builder()
                .peakPrice(peak)
                .order(Order.builder().id(orderId).userId(7L).symbol("TCS")
                        .margin(Margin.builder().symbol("TCS").token("11536").build()).build())
                .build();
    }

    // --- target trades ----------------------------------------------------

    @Test
    void onTick_publishesCompletionOnceTheTargetIsReached() {
        watchdog.watch(trade("s1", 3300));

        watchdog.onTick("11536", 3300.0);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertInstanceOf(TradeCompletionEvent.class, event.getValue());
        assertEquals(7L, ((TradeCompletionEvent) event.getValue()).userId());
    }

    @Test
    void onTick_staysQuietBelowTheTarget() {
        watchdog.watch(trade("s1", 3300));

        watchdog.onTick("11536", 3299.95);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void onTick_firesOnlyOnceUntilTheTriggerIsCleared() {
        // Ticks arrive many times a second; re-firing would place duplicate exit checks.
        watchdog.watch(trade("s1", 3300));

        watchdog.onTick("11536", 3301.0);
        watchdog.onTick("11536", 3302.0);

        verify(applicationEventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void clearTrigger_allowsTheSameTradeToFireAgain() {
        var trade = trade("s1", 3300);
        watchdog.watch(trade);

        watchdog.onTick("11536", 3301.0);
        watchdog.clearTrigger(trade);
        watchdog.onTick("11536", 3302.0);

        verify(applicationEventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void onTick_ignoresNonPositivePrices() {
        // getLTP returns -1/-2 sentinels for "unknown" and "disconnected".
        watchdog.watch(trade("s1", 3300));

        watchdog.onTick("11536", -1.0);
        watchdog.onTick("11536", 0.0);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void onTick_ignoresTokensThatAreNotWatched() {
        watchdog.watch(trade("s1", 3300));

        watchdog.onTick("99999", 9999.0);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unwatch_stopsFurtherCompletionEvents() {
        var trade = trade("s1", 3300);
        watchdog.watch(trade);
        watchdog.unwatch(trade);

        watchdog.onTick("11536", 3301.0);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void watch_accumulatesMultipleTradesOnTheSameToken() {
        watchdog.watch(trade("s1", 3300));
        watchdog.watch(trade("s2", 3300));

        assertEquals(1, watchdog.getWatchedTokenCount());
        assertEquals(2, watchdog.getWatchedTradeCount());
    }

    @Test
    void unwatch_isSafeForATokenThatWasNeverWatched() {
        watchdog.unwatch(trade("ghost", 1));
        assertEquals(0, watchdog.getWatchedTradeCount());
    }

    // --- MTF trades -------------------------------------------------------

    @Test
    void onTick_publishesAnMtfEventAndTracksThePeak() {
        var trade = mtfTrade("o1", 100.0);
        watchdog.watchMtfTrade(trade);

        watchdog.onTick("11536", 105.0);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        var mtfEvent = assertInstanceOf(MtfTickEvent.class, event.getValue());
        assertEquals(105.0, mtfEvent.ltp());
        assertEquals(105.0, mtfEvent.peakPrice(), "the peak must ratchet up so the trail follows it");
    }

    @Test
    void onTick_neverLowersTheRecordedPeak() {
        var trade = mtfTrade("o1", 110.0);
        watchdog.watchMtfTrade(trade);

        watchdog.onTick("11536", 105.0);

        assertEquals(110.0, trade.getPeakPrice());
    }

    @Test
    void onTick_skipsAnMtfTradeWhenThePriceHasNotMoved() {
        var trade = mtfTrade("o1", 100.0);
        trade.setPrevLtp(105.0);
        watchdog.watchMtfTrade(trade);

        watchdog.onTick("11536", 105.0);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void clearMtfTrigger_allowsTheNextTickToFire() {
        var trade = mtfTrade("o1", 100.0);
        watchdog.watchMtfTrade(trade);

        watchdog.onTick("11536", 105.0);
        watchdog.clearMtfTrigger(trade);
        watchdog.onTick("11536", 106.0);

        verify(applicationEventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void unwatchMtfTrade_stopsFurtherEvents() {
        var trade = mtfTrade("o1", 100.0);
        watchdog.watchMtfTrade(trade);
        watchdog.unwatchMtfTrade(trade);

        watchdog.onTick("11536", 105.0);

        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void counters_reflectWatchedAndInFlightState() {
        watchdog.watch(trade("s1", 3300));
        watchdog.watchMtfTrade(mtfTrade("o1", 100.0));

        watchdog.onTick("11536", 3301.0);

        assertEquals(1, watchdog.getMtfWatchedTokenCount());
        assertEquals(1, watchdog.getMtfWatchedTradeCount());
        assertEquals(1, watchdog.getInFlightTriggerCount());
        assertEquals(1, watchdog.getInFlightMtfTriggerCount());
    }

    @Test
    void purgeAtSquareOff_leavesTheCachesAloneWhileTheMarketIsOpen() {
        watchdog.watch(trade("s1", 3300));

        watchdog.purgeAtSquareOff();

        assertEquals(1, watchdog.getWatchedTradeCount());
    }
}
