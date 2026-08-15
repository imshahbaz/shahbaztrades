package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.StrategyOrderDto;
import com.app.shahbaztrades.model.entity.StrategyOrder;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.StrategyOrderRepo;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyOrderServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private StrategyOrderRepo strategyOrderRepo;
    @Mock
    private UserService userService;

    private StrategyOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyOrderServiceImpl(mongoTemplate, strategyOrderRepo, userService);
    }

    private void stubRupeezyUser() {
        var config = new User.RupeezyConfig();
        config.setAppId("app");
        config.setApiSecret("secret");
        lenient().when(userService.findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong()))
                .thenReturn(User.builder().userId(4L).rupeezyConfig(config).build());
    }

    private StrategyOrderDto request() {
        return StrategyOrderDto.builder()
                .userId(4L).strategyName("RSI15MIN")
                .date(DateUtil.getTodayDate().plusDays(1).toString())
                .amount(new BigDecimal("10000")).broker(BrokerType.RUPEEZY)
                .build();
    }

    private StrategyOrder entity(String id) {
        return StrategyOrder.builder()
                .id(id).userId(4L).strategyName("RSI15MIN").amount(new BigDecimal("10000"))
                .broker(BrokerType.RUPEEZY).date(Instant.parse("2020-01-15T03:30:00Z"))
                .build();
    }

    @Test
    void createOrder_insertsAndReturnsTheSavedDto() {
        stubRupeezyUser();
        when(strategyOrderRepo.insert(any(StrategyOrder.class))).thenReturn(entity("s1"));

        StrategyOrderDto saved = service.createOrder(request());

        assertEquals("s1", saved.getId());
        assertEquals(BrokerType.RUPEEZY, saved.getBroker());
    }

    @Test
    void createOrder_rejectsABrokerTheUserHasNotRegistered() {
        // The user only has Rupeezy configured, so a Zerodha order must be refused before insert.
        stubRupeezyUser();
        var request = request();
        request.setBroker(BrokerType.ZERODHA);

        assertThrows(BadRequestException.class, () -> service.createOrder(request));
        verify(strategyOrderRepo, never()).insert(any(StrategyOrder.class));
    }

    @Test
    void createOrder_translatesADuplicateKeyIntoAConflict() {
        stubRupeezyUser();
        when(strategyOrderRepo.insert(any(StrategyOrder.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.createOrder(request()));
    }

    @Test
    void updateOrder_savesAndReturnsTheUpdatedDto() {
        stubRupeezyUser();
        when(strategyOrderRepo.save(any(StrategyOrder.class))).thenReturn(entity("s1"));

        assertEquals("s1", service.updateOrder(request()).getId());
    }

    @Test
    void updateOrder_translatesADuplicateKeyIntoAConflict() {
        stubRupeezyUser();
        when(strategyOrderRepo.save(any(StrategyOrder.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.updateOrder(request()));
    }

    @Test
    void getOrderById_throwsForAnUnknownId() {
        when(strategyOrderRepo.findById("missing")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrderById("missing"));
    }

    @Test
    void getOrderById_mapsTheEntityToItsDto() {
        when(strategyOrderRepo.findById("s1")).thenReturn(Optional.of(entity("s1")));

        assertEquals("RSI15MIN", service.getOrderById("s1").getStrategyName());
    }

    @Test
    void deleteOrder_validatesTheTradingWindowBeforeDeleting() {
        // The order dates to 2020, whose 09:00-15:30 window is long past -> deletion allowed.
        when(strategyOrderRepo.findById("s1")).thenReturn(Optional.of(entity("s1")));

        service.deleteOrder("s1");

        verify(strategyOrderRepo).deleteById("s1");
    }

    @Test
    void deleteOrder_refusesWhileTheOrdersOwnSessionIsLive() {
        var live = entity("s1");
        live.setDate(DateUtil.getTodayDate().atStartOfDay(DateUtil.IST_ZONE).toInstant());
        when(strategyOrderRepo.findById("s1")).thenReturn(Optional.of(live));

        var now = java.time.ZonedDateTime.now(DateUtil.IST_ZONE).toLocalTime();
        boolean inSession = now.isAfter(java.time.LocalTime.of(9, 0))
                && now.isBefore(java.time.LocalTime.of(15, 30));

        if (inSession) {
            assertThrows(BadRequestException.class, () -> service.deleteOrder("s1"));
            verify(strategyOrderRepo, never()).deleteById(anyString());
        } else {
            service.deleteOrder("s1");
            verify(strategyOrderRepo).deleteById("s1");
        }
    }

    @Test
    void getAllOrdersAdmin_filtersByStrategyAndSortsNewestFirst() {
        when(mongoTemplate.find(any(Query.class), eq(StrategyOrder.class)))
                .thenReturn(List.of(entity("s1"), entity("s2")));

        assertEquals(2, service.getAllOrdersAdmin("RSI15MIN").size());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(StrategyOrder.class));
        assertTrue(query.getValue().getQueryObject().toJson().contains("RSI15MIN"));
        assertTrue(query.getValue().getSortObject().toJson().contains("-1"), "newest orders must come first");
    }

    @Test
    void getOrdersByUserId_scopesTheQueryToThatUser() {
        when(mongoTemplate.find(any(Query.class), eq(StrategyOrder.class))).thenReturn(List.of(entity("s1")));

        assertEquals(1, service.getOrdersByUserId(4L).size());

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(StrategyOrder.class));
        assertTrue(query.getValue().getQueryObject().toJson().contains("userId"));
    }

    @Test
    void getTodayOrders_boundsTheQueryToTheIstDay() {
        when(mongoTemplate.find(any(Query.class), eq(StrategyOrder.class))).thenReturn(List.of());

        service.getTodayOrders();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(StrategyOrder.class));
        var bounds = (org.bson.Document) query.getValue().getQueryObject().get(StrategyOrder.Fields.date);

        // A UTC-day window would silently drop the 00:00-05:30 IST slice of orders.
        assertEquals(DateUtil.getTodayDate().atStartOfDay(DateUtil.IST_ZONE).toInstant(), bounds.get("$gte"));
        assertEquals(DateUtil.getTodayDate().plusDays(1).atStartOfDay(DateUtil.IST_ZONE).toInstant(),
                bounds.get("$lt"));
    }
}
