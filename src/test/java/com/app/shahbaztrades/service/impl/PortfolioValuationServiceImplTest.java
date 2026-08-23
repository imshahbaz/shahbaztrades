package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.trading.BrokerMarginPolicyFactory;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import com.app.shahbaztrades.model.dto.holdings.HoldingDto;
import com.app.shahbaztrades.model.entity.Holdings;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.model.enums.BrokerType;
import com.app.shahbaztrades.repo.HoldingsRepo;
import com.app.shahbaztrades.repo.redis.HoldingsDataRedisRepo;
import com.app.shahbaztrades.service.MarketDataQuery;
import com.app.shahbaztrades.service.MarginService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceImplTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MarginService marginService;
    @Mock
    private MarketDataQuery marketDataQuery;
    @Mock
    private BrokerMarginPolicyFactory brokerMarginPolicyFactory;
    @Mock
    private HoldingsDataRedisRepo<Holdings> holdingsDataRedisRepo;

    private PortfolioValuationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PortfolioValuationServiceImpl(mongoTemplate, marginService, marketDataQuery,
                brokerMarginPolicyFactory, holdingsDataRedisRepo);
    }

    @Test
    void updatePortfolio_isANoOpWhenNobodyHoldsZerodhaPositions() {
        when(mongoTemplate.find(any(Query.class), eq(Holdings.class))).thenReturn(List.of());

        service.updatePortfolio();

        verify(mongoTemplate, never()).bulkOps(any(), eq(Holdings.class));
    }
}
