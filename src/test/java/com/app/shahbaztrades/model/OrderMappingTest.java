package com.app.shahbaztrades.model;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.model.enums.OrderStatus;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMappingTest {

    private static final Margin MARGIN = Margin.builder()
            .symbol("TCS").token("11536").requiredMargin(new BigDecimal("4.5")).build();

    private OrderDto dto(String date) {
        return OrderDto.builder()
                .id("o1").userId(7L).symbol("TCS").quantity(10).date(date)
                .broker(BrokerType.ZERODHA).strategyName("TRAILING PROFIT")
                .build();
    }

    @Test
    void toEntity_mapsFieldsAndPinsTheDateToIstMidnight() {
        String tomorrow = DateUtil.getTodayDate().plusDays(1).toString();

        Order order = dto(tomorrow).toEntity(MARGIN);

        assertEquals("o1", order.getId());
        assertEquals(7L, order.getUserId());
        assertEquals(10, order.getQuantity());
        assertEquals(MARGIN, order.getMargin());
        assertEquals(LocalDate.parse(tomorrow).atStartOfDay(DateUtil.IST_ZONE).toInstant(), order.getDate());
    }

    @Test
    void toEntity_defaultsANewOrderToPending() {
        Order order = dto(DateUtil.getTodayDate().plusDays(1).toString()).toEntity(MARGIN);
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
    }

    @Test
    void toEntity_rejectsAMalformedDate() {
        assertThrows(BadRequestException.class, () -> dto("15/08/2026").toEntity(MARGIN));
        assertThrows(BadRequestException.class, () -> dto("not-a-date").toEntity(MARGIN));
    }

    @Test
    void toEntity_rejectsADateWhoseCutoffHasPassed() {
        // OrderDto delegates to OrderValidator: a past trading day can never be booked.
        assertThrows(BadRequestException.class, () -> dto("2000-01-01").toEntity(MARGIN));
    }

    @Test
    void toDto_roundTripsBackToAnIsoDateString() {
        String tomorrow = DateUtil.getTodayDate().plusDays(1).toString();

        OrderDto roundTripped = dto(tomorrow).toEntity(MARGIN).toDto();

        assertEquals(tomorrow, roundTripped.getDate());
        assertEquals("TCS", roundTripped.getSymbol());
        assertEquals(BrokerType.ZERODHA, roundTripped.getBroker());
    }

    @Test
    void statusLabelAndColour_areNullSafeOnTheDto() {
        OrderDto dto = OrderDto.builder().build();
        assertNull(dto.getStatusLabel());
        assertNull(dto.getStatusColor());

        dto.setOrderStatus(OrderStatus.BOUGHT);
        assertEquals(OrderStatus.BOUGHT.getLabel(), dto.getStatusLabel());
        assertEquals(OrderStatus.BOUGHT.getColor(), dto.getStatusColor());
    }

    @Test
    void hasEntryOrder_needsANonBlankBrokerOrderId() {
        assertFalse(Order.builder().build().hasEntryOrder());
        assertFalse(Order.builder().entry(Order.ExecutionRecord.builder().build()).build().hasEntryOrder());
        assertFalse(Order.builder()
                .entry(Order.ExecutionRecord.builder().brokerOrderId("").build()).build().hasEntryOrder());
        assertTrue(Order.builder()
                .entry(Order.ExecutionRecord.builder().brokerOrderId("B1").build()).build().hasEntryOrder());
    }

    @Test
    void hasExitOrder_needsANonBlankBrokerOrderId() {
        assertFalse(Order.builder().build().hasExitOrder());
        assertTrue(Order.builder()
                .exit(Order.ExecutionRecord.builder().brokerOrderId("X1").build()).build().hasExitOrder());
    }

    @Test
    void hasEntryPrice_requiresAPositiveAveragePrice() {
        // A zero fill price would make every downstream stop-loss percentage meaningless.
        assertFalse(Order.builder().build().hasEntryPrice());
        assertFalse(Order.builder()
                .entry(Order.ExecutionRecord.builder().averagePrice(BigDecimal.ZERO).build())
                .build().hasEntryPrice());
        assertTrue(Order.builder()
                .entry(Order.ExecutionRecord.builder().averagePrice(new BigDecimal("101.5")).build())
                .build().hasEntryPrice());
    }

    @Test
    void toEntity_carriesTheTargetPercentageForTargetProfitOrders() {
        OrderDto dto = dto(DateUtil.getTodayDate().plusDays(1).toString());
        dto.setStrategyName("TARGET PROFIT");
        dto.setTargetPercentage(new BigDecimal("1.5"));

        Order order = dto.toEntity(MARGIN);

        assertNotNull(order.getTargetPercentage());
        assertEquals(0, new BigDecimal("1.5").compareTo(order.getTargetPercentage()));
    }
}
