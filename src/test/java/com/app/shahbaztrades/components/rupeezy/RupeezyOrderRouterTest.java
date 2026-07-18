package com.app.shahbaztrades.components.rupeezy;

import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.order.TradeOrderResponse;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyOrderHistory;
import com.app.shahbaztrades.model.dto.rupeezy.RupeezyTokenCache;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.service.RupeezyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RupeezyOrderRouterTest {

    @Mock
    private RupeezyClient rupeezyClient;
    @Mock
    private RupeezyService rupeezyService;

    private RupeezyOrderRouter router;

    @BeforeEach
    void setUp() {
        router = new RupeezyOrderRouter(rupeezyClient, rupeezyService);
        RupeezyTokenCache cache = new RupeezyTokenCache();
        lenient().when(rupeezyService.getTokenCache(anyLong())).thenReturn(cache);
    }

    private RupeezyOrderHistory history(String status, RupeezyOrderHistory.OrderData... orders) {
        RupeezyOrderHistory h = new RupeezyOrderHistory();
        h.setStatus(status);
        h.setOrders(List.of(orders));
        return h;
    }

    @Test
    void getBrokerType_isRupeezy() {
        assertEquals(BrokerType.RUPEEZY, router.getBrokerType());
    }

    @Test
    void getOrderDetails_mapsFieldsIncludingBigDecimalPrice() {
        var order = RupeezyOrderHistory.OrderData.builder()
                .orderId("O1").status("COMPLETE").pendingQuantity(5).averagePrice(123.45).build();
        when(rupeezyClient.getOrder(any(), any())).thenReturn(history("success", order));

        TradeOrderResponse resp = router.getOrderDetails(42L, "O1");

        assertEquals("O1", resp.getOrderId());
        assertEquals("COMPLETE", resp.getStatus());
        assertEquals(5, resp.getPendingQuantity());
        assertEquals(0, resp.getAveragePrice().compareTo(BigDecimal.valueOf(123.45)));
    }

    @Test
    void getOrderDetails_unknownOrderId_throwsNotFound() {
        var order = RupeezyOrderHistory.OrderData.builder().orderId("OTHER").status("OPEN").build();
        when(rupeezyClient.getOrder(any(), any())).thenReturn(history("success", order));

        assertThrows(NotFoundException.class, () -> router.getOrderDetails(42L, "O1"));
    }

    @Test
    void getOrderDetails_unsuccessfulResponse_throwsNotFound() {
        when(rupeezyClient.getOrder(any(), any())).thenReturn(history("error"));
        assertThrows(NotFoundException.class, () -> router.getOrderDetails(42L, "O1"));
    }

    @Test
    void getOrderDetails_missingTokenCache_throwsNotFound() {
        when(rupeezyService.getTokenCache(anyLong())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> router.getOrderDetails(42L, "O1"));
    }
}
