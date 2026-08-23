package com.app.shahbaztrades.service.impl;

import org.mockito.InjectMocks;
import com.app.shahbaztrades.service.OrderService;
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
class MtfTradeEngineImplTest {

    @Mock
    private OrderService orderService;
    @Mock
    private DailyTradingStrategyRegistry dailyTradingStrategyRegistry;

    @Mock
    private DailyTradingStrategy strategy;

    @InjectMocks
    private MtfTradeEngineImpl service;

    private Order order(String id, String strategyName) {
        return Order.builder()
                .id(id).userId(7L).symbol("TCS").quantity(10)
                .broker(BrokerType.ZERODHA).strategyName(strategyName)
                .date(Instant.parse("2020-01-15T03:30:00Z"))
                .build();
    }

    @Test
    void initiateMtfOrders_dispatchesEachOrderToItsStrategy() {
        when(orderService.getTodayOrders()).thenReturn(List.of(order("o1", "TARGET PROFIT"), order("o2", "TRAILING PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy(anyString())).thenReturn(strategy);

        service.initiateMtfOrders();

        verify(strategy, times(2)).initialiseTrade(any(Order.class), anyMap());
    }

    @Test
    void initiateMtfOrders_sharesOneMetricsMapAcrossOrdersToAvoidRefetchingAtr() {
        when(orderService.getTodayOrders()).thenReturn(List.of(order("o1", "TARGET PROFIT"), order("o2", "TARGET PROFIT")));
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
        when(orderService.getTodayOrders()).thenReturn(List.of(order("o1", "BAD"), order("o2", "TRAILING PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TRAILING PROFIT")).thenReturn(strategy);

        service.initiateMtfOrders();

        verify(strategy).initialiseTrade(any(Order.class), anyMap());
    }

    @Test
    void initiateMtfOrders_isANoOpWhenThereAreNoOrders() {
        when(orderService.getTodayOrders()).thenReturn(List.of());

        service.initiateMtfOrders();

        verify(dailyTradingStrategyRegistry, never()).getStrategy(anyString());
    }

    @Test
    void updateMtfOrderStatus_dispatchesEachOrderAndSurvivesFailures() {
        when(orderService.getTodayOrders()).thenReturn(List.of(order("o1", "BAD"), order("o2", "TARGET PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TARGET PROFIT")).thenReturn(strategy);

        service.updateMtfOrderStatus();

        verify(strategy).updateTradeStatus(any(Order.class));
    }

    @Test
    void startTrading_dispatchesEachOrderAndSurvivesFailures() {
        when(orderService.getTodayOrders()).thenReturn(List.of(order("o1", "BAD"), order("o2", "TARGET PROFIT")));
        when(dailyTradingStrategyRegistry.getStrategy("BAD")).thenThrow(new NotFoundException("no strategy"));
        when(dailyTradingStrategyRegistry.getStrategy("TARGET PROFIT")).thenReturn(strategy);

        service.startTrading();

        verify(strategy).startTrading(any(Order.class));
    }
}
