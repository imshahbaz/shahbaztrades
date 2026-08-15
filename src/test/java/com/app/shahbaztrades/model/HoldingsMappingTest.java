package com.app.shahbaztrades.model;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldingsMappingTest {

    private HoldingDto.HoldingDetailDto detailDto(String buyDate) {
        return HoldingDto.HoldingDetailDto.builder()
                .id(1).quantity(5).price(new BigDecimal("100.25")).buyDate(buyDate).build();
    }

    @Test
    void toHoldingDetail_parsesTheBuyDateAtIstMidnight() {
        var detail = detailDto("2026-08-15").toHoldingDetail();

        assertEquals(LocalDate.of(2026, 8, 15).atStartOfDay(DateUtil.IST_ZONE).toInstant(), detail.getBuyDate());
        assertEquals(5, detail.getQuantity());
        assertEquals(0, new BigDecimal("100.25").compareTo(detail.getPrice()));
    }

    @Test
    void toHoldingDetail_rejectsANonIsoDate() {
        assertThrows(BadRequestException.class, () -> detailDto("15-08-2026").toHoldingDetail());
        assertThrows(BadRequestException.class, () -> detailDto("").toHoldingDetail());
    }

    @Test
    void holdingDetail_roundTripsThroughItsDto() {
        var detail = detailDto("2026-08-15").toHoldingDetail();

        assertEquals("2026-08-15", detail.toHoldingDetailDto().getBuyDate());
        assertEquals(1, detail.toHoldingDetailDto().getId());
    }

    @Test
    void holdingInfo_toDtoCopiesEveryDetailRow() {
        var info = Holdings.HoldingInfo.builder()
                .symbol("TCS").margin(4.5f).ltp(new BigDecimal("3200"))
                .holdingDetails(new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
                        detailDto("2026-08-15").toHoldingDetail(),
                        detailDto("2026-08-16").toHoldingDetail())))
                .build();

        HoldingDto dto = info.toHoldingDto();

        assertEquals("TCS", dto.getSymbol());
        assertEquals(4.5f, dto.getMargin());
        assertEquals(2, dto.getHoldingDetails().size());
        assertEquals(0, new BigDecimal("3200").compareTo(dto.getLtp()));
    }

    @Test
    void holdings_defaultsToAnEmptyMutableBrokerMap() {
        // createHoldings does computeIfAbsent on this map, so it must never be null or immutable.
        Holdings holdings = Holdings.builder().userId(1L).build();

        assertNotNull(holdings.getBrokerHoldingMap());
        assertTrue(holdings.getBrokerHoldingMap().isEmpty());
        holdings.getBrokerHoldingMap().put(BrokerType.ZERODHA, new java.util.concurrent.CopyOnWriteArrayList<>());
        assertEquals(1, holdings.getBrokerHoldingMap().size());
    }

    @Test
    void holdingInfo_defaultsToAnEmptyMutableDetailList() {
        var info = Holdings.HoldingInfo.builder().symbol("TCS").build();

        assertNotNull(info.getHoldingDetails());
        info.getHoldingDetails().add(detailDto("2026-08-15").toHoldingDetail());
        assertEquals(1, info.getHoldingDetails().size());
    }
}
