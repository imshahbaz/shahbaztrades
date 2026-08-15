package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Covers validateOrder / validateBroker / validateForDelete, which OrderValidatorTest does not. */
class OrderValidatorRulesTest {

    private Order order(String strategyName, BigDecimal targetPercentage) {
        return Order.builder().strategyName(strategyName).targetPercentage(targetPercentage).build();
    }

    private User zerodhaUser() {
        var config = new User.ZerodhaConfig();
        config.setApiKey("key");
        config.setApiSecret("secret");
        return User.builder().zerodhaConfig(config).build();
    }

    private User rupeezyUser() {
        var config = new User.RupeezyConfig();
        config.setAppId("app");
        config.setApiSecret("secret");
        return User.builder().rupeezyConfig(config).build();
    }

    @Test
    void validateOrder_targetProfitAcceptsPercentagesInsideTheBand() {
        assertDoesNotThrow(() -> OrderValidator.validateOrder(order("TARGET PROFIT", new BigDecimal("0.4"))));
        assertDoesNotThrow(() -> OrderValidator.validateOrder(order("TARGET PROFIT", new BigDecimal("20"))));
        assertDoesNotThrow(() -> OrderValidator.validateOrder(order("TARGET PROFIT", new BigDecimal("5.5"))));
    }

    @Test
    void validateOrder_targetProfitRejectsPercentagesOutsideTheBand() {
        // Below 0.4% the target is eaten by brokerage; above 20% it is not a same-day exit.
        assertThrows(BadRequestException.class,
                () -> OrderValidator.validateOrder(order("TARGET PROFIT", new BigDecimal("0.39"))));
        assertThrows(BadRequestException.class,
                () -> OrderValidator.validateOrder(order("TARGET PROFIT", new BigDecimal("20.01"))));
    }

    @Test
    void validateOrder_targetProfitRequiresAPercentage() {
        assertThrows(BadRequestException.class, () -> OrderValidator.validateOrder(order("TARGET PROFIT", null)));
    }

    @Test
    void validateOrder_trailingProfitClearsAnyStaleTargetPercentage() {
        var order = order("TRAILING PROFIT", new BigDecimal("5"));

        OrderValidator.validateOrder(order);

        // A trailing order must not carry a fixed target, or the exit logic reads a stale value.
        assertNull(order.getTargetPercentage());
    }

    @Test
    void validateOrder_rejectsAnUnknownStrategy() {
        assertThrows(BadRequestException.class, () -> OrderValidator.validateOrder(order("MOMENTUM", null)));
    }

    @Test
    void validateBroker_acceptsAUserWithMatchingBrokerConfig() {
        assertDoesNotThrow(() -> OrderValidator.validateBroker(zerodhaUser(), BrokerType.ZERODHA));
        assertDoesNotThrow(() -> OrderValidator.validateBroker(rupeezyUser(), BrokerType.RUPEEZY));
    }

    @Test
    void validateBroker_rejectsAUserWhoNeverRegisteredThatBroker() {
        // A Zerodha-only user must not be able to route an order through Rupeezy.
        assertThrows(BadRequestException.class,
                () -> OrderValidator.validateBroker(zerodhaUser(), BrokerType.RUPEEZY));
        assertThrows(BadRequestException.class,
                () -> OrderValidator.validateBroker(rupeezyUser(), BrokerType.ZERODHA));
    }

    @Test
    void validateForDelete_rejectsDeletionDuringTheTradingWindow() {
        // The order's own date defines the window, so "today" is inside it iff we are mid-session.
        var todayStart = DateUtil.getTodayDate().atStartOfDay(DateUtil.IST_ZONE).toInstant();
        var nowIst = java.time.ZonedDateTime.now(DateUtil.IST_ZONE).toLocalTime();
        boolean inSession = nowIst.isAfter(java.time.LocalTime.of(9, 0))
                && nowIst.isBefore(java.time.LocalTime.of(15, 30));

        if (inSession) {
            assertThrows(BadRequestException.class, () -> OrderValidator.validateForDelete(todayStart));
        } else {
            assertDoesNotThrow(() -> OrderValidator.validateForDelete(todayStart));
        }
    }

    @Test
    void validateForDelete_allowsDeletingAnOrderFromAnotherDay() {
        // Yesterday's 09:00-15:30 window is long past, so deletion is always allowed.
        var yesterday = DateUtil.getTodayDate().minusDays(1).atStartOfDay(DateUtil.IST_ZONE).toInstant();
        assertDoesNotThrow(() -> OrderValidator.validateForDelete(yesterday));
    }

    @Test
    void validateOrderDate_rejectsTodayOnceThe9amCutoffHasPassed() {
        LocalDate today = DateUtil.getTodayDate();
        boolean pastCutoff = java.time.ZonedDateTime.now(DateUtil.IST_ZONE)
                .toLocalTime().isAfter(java.time.LocalTime.of(9, 0));

        if (pastCutoff) {
            assertThrows(BadRequestException.class, () -> OrderValidator.validateOrderDate(today));
        } else {
            assertDoesNotThrow(() -> OrderValidator.validateOrderDate(today));
        }
    }

    @Test
    void orderStatusEnum_exposesLabelAndColourForEveryState() {
        for (var status : com.app.shahbaztrades.model.enums.OrderStatus.values()) {
            assertEquals(7, status.getColor().length(), "colour must be a #rrggbb hex string");
            assertDoesNotThrow(status::getLabel);
        }
    }
}
