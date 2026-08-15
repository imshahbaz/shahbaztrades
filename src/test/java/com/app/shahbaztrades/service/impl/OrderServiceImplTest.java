package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.observer.TradeWatchdog;
import com.app.shahbaztrades.components.orderrouting.OrderRouterFactory;
import com.app.shahbaztrades.components.strategy.DailyTradingStrategy;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.order.OrderDto;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.entity.Order;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.OrderRepo;
import com.app.shahbaztrades.service.MarginService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepo orderRepo;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MarginService marginService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TradeWatchdog tradeWatchdog;
    @Mock
    private OrderRouterFactory orderRouterFactory;
    @Mock
    private UserService userService;
    @Mock
    private DailyTradingStrategyRegistry dailyTradingStrategyRegistry;
    @Mock
    private DailyTradingStrategy strategy;

    private OrderServiceImpl service;

    private static final Margin MARGIN = Margin.builder()
            .symbol("TCS").token("11536").requiredMargin(new BigDecimal("4.5")).build();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepo, mongoTemplate, marginService, eventPublisher,
                tradeWatchdog, orderRouterFactory, userService, dailyTradingStrategyRegistry);
    }

    private void stubZerodhaUser() {
        var config = new User.ZerodhaConfig();
        config.setApiKey("key");
        config.setApiSecret("secret");
        lenient().when(userService.findByUserIdOrEmailOrMobile(anyLong(), anyString(), anyLong()))
                .thenReturn(User.builder().userId(7L).zerodhaConfig(config).build());
    }

    private OrderDto dto() {
        return OrderDto.builder()
                .userId(7L).symbol("tcs").quantity(10)
                .date(DateUtil.getTodayDate().plusDays(1).toString())
                .broker(BrokerType.ZERODHA).strategyName("TRAILING PROFIT")
                .build();
    }

    private Order order(String id, String strategyName) {
        return Order.builder()
                .id(id).userId(7L).symbol("TCS").quantity(10).margin(MARGIN)
                .broker(BrokerType.ZERODHA).strategyName(strategyName)
                .date(Instant.parse("2020-01-15T03:30:00Z"))
                .build();
    }

    // --- reads ------------------------------------------------------------

    @Test
    void getById_throwsForAnUnknownOrder() {
        when(orderRepo.findById("nope")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById("nope"));
    }

    @Test
    void getById_mapsToTheDto() {
        when(orderRepo.findById("o1")).thenReturn(Optional.of(order("o1", "TRAILING PROFIT")));
        assertEquals("TCS", service.getById("o1").getSymbol());
    }

    @Test
    void getOrdersByDate_rejectsAMalformedDate() {
        assertThrows(BadRequestException.class, () -> service.getOrdersByDate("15/08/2026"));
        verify(mongoTemplate, never()).find(any(Query.class), eq(Order.class));
    }

    @Test
    void getOrdersByDate_boundsTheQueryToTheIstDay() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        service.getOrdersByDate("2026-08-15");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Order.class));
        var bounds = (org.bson.Document) query.getValue().getQueryObject().get(Order.Fields.date);
        assertEquals(java.time.LocalDate.of(2026, 8, 15).atStartOfDay(DateUtil.IST_ZONE).toInstant(),
                bounds.get("$gte"));
        assertEquals(java.time.LocalDate.of(2026, 8, 16).atStartOfDay(DateUtil.IST_ZONE).toInstant(),
                bounds.get("$lt"));
    }

    @Test
    void getTodayOrders_boundsTheQueryToTheCurrentIstDay() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        service.getTodayOrders();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Order.class));
        var bounds = (org.bson.Document) query.getValue().getQueryObject().get(Order.Fields.date);
        assertEquals(DateUtil.getTodayDate().atStartOfDay(DateUtil.IST_ZONE).toInstant(), bounds.get("$gte"));
    }

    // --- writes -----------------------------------------------------------

    @Test
    void createOrder_looksUpTheMarginByUpperCaseSymbol() {
        stubZerodhaUser();
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));

        service.createOrder(dto());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(mongoTemplate).insert(saved.capture());
        assertEquals(MARGIN, saved.getValue().getMargin());
    }

    @Test
    void createOrder_throwsWhenTheSymbolHasNoMargin() {
        // No margin means the stock is not MTF-eligible; the order must not be persisted.
        when(marginService.getMarginCache()).thenReturn(Map.of());

        assertThrows(NotFoundException.class, () -> service.createOrder(dto()));
        verify(mongoTemplate, never()).insert(any(Order.class));
    }

    @Test
    void createOrder_rejectsAnInvalidStrategy() {
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));
        var dto = dto();
        dto.setStrategyName("MOMENTUM");

        assertThrows(BadRequestException.class, () -> service.createOrder(dto));
    }

    @Test
    void createOrder_rejectsABrokerTheUserHasNotRegistered() {
        stubZerodhaUser();
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));
        var dto = dto();
        dto.setBroker(BrokerType.RUPEEZY);

        assertThrows(BadRequestException.class, () -> service.createOrder(dto));
    }

    @Test
    void createOrder_translatesADuplicateKeyIntoAConflict() {
        stubZerodhaUser();
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));
        when(mongoTemplate.insert(any(Order.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.createOrder(dto()));
    }

    @Test
    void updateOrder_savesThroughTheRepository() {
        stubZerodhaUser();
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));

        service.updateOrder(dto());

        verify(orderRepo).save(any(Order.class));
    }

    @Test
    void updateOrder_translatesADuplicateKeyIntoAConflict() {
        stubZerodhaUser();
        when(marginService.getMarginCache()).thenReturn(Map.of("TCS", MARGIN));
        when(orderRepo.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.updateOrder(dto()));
    }

    @Test
    void deleteOrder_validatesTheWindowThenDeletes() {
        when(orderRepo.findById("o1")).thenReturn(Optional.of(order("o1", "TRAILING PROFIT")));

        service.deleteOrder("o1");

        verify(orderRepo).deleteById("o1");
    }

    @Test
    void deleteOrder_throwsForAnUnknownOrder() {
        when(orderRepo.findById("nope")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.deleteOrder("nope"));
        verify(orderRepo, never()).deleteById(anyString());
    }

    // --- daily batch jobs -------------------------------------------------

    @Test
    void initiateMtfOrders_dispatchesEachOrderToItsStrategy() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(order("o1", "TARGET PROFIT"), order("o2", "TRAILING PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy(anyString())).thenReturn(strategy);

        service.initiateMtfOrders();

        verify(strategy, times(2)).initialiseTrade(any(Order.class), anyMap());
    }

    @Test
    void initiateMtfOrders_sharesOneMetricsMapAcrossOrdersToAvoidRefetchingAtr() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(order("o1", "TARGET PROFIT"), order("o2", "TARGET PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy(anyString())).thenReturn(strategy);

        service.initiateMtfOrders();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> metrics = ArgumentCaptor.forClass(Map.class);
        verify(strategy, times(2)).initialiseTrade(any(Order.class), metrics.capture());
        assertSame(metrics.getAllValues().getFirst(), metrics.getAllValues().getLast(),
                "a fresh map per order would re-download ATR history for every duplicate symbol");
    }

    @Test
    void initiateMtfOrders_keepsGoingWhenOneOrderFails() {
        // One user's broker outage must not stop every other user's morning orders.
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(order("o1", "BAD"), order("o2", "TRAILING PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TRAILING PROFIT")).thenReturn(strategy);

        service.initiateMtfOrders();

        verify(strategy).initialiseTrade(any(Order.class), anyMap());
    }

    @Test
    void initiateMtfOrders_isANoOpWhenThereAreNoOrders() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of());

        service.initiateMtfOrders();

        verify(dailyTradingStrategyRegistry, never()).getStrategy(anyString());
    }

    @Test
    void updateMtfOrderStatus_dispatchesEachOrderAndSurvivesFailures() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(order("o1", "BAD"), order("o2", "TARGET PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TARGET PROFIT")).thenReturn(strategy);

        service.updateMtfOrderStatus();

        verify(strategy).updateTradeStatus(any(Order.class));
    }

    @Test
    void startTrading_dispatchesEachOrderAndSurvivesFailures() {
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(order("o1", "BAD"), order("o2", "TARGET PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TARGET PROFIT")).thenReturn(strategy);

        service.startTrading();

        verify(strategy).startTrading(any(Order.class));
    }
}
