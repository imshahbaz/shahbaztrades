package com.app.shahbaztrades.service.impl;

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
import com.app.shahbaztrades.components.trading.BrokerMarginPolicyFactory;
import com.app.shahbaztrades.components.trading.ZerodhaMarginPolicy;
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
class HoldingsServiceImplTest {

    @Mock
    private HoldingsRepo holdingsRepo;
    @Mock
    private MarginService marginService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MarketDataQuery marketDataQuery;
    @Mock
    private HoldingsDataRedisRepo<Holdings> holdingsDataRedisRepo;

    @Mock
    private BrokerMarginPolicyFactory brokerMarginPolicyFactory;

    private HoldingsServiceImpl service;

    /** Zerodha's real policy: leverage comes from the requiredMargin field. */
    private void stubZerodhaMarginPolicy() {
        when(brokerMarginPolicyFactory.getPolicy(BrokerType.ZERODHA)).thenReturn(new ZerodhaMarginPolicy());
    }

    private static final UserDto USER = UserDto.builder().userId(7L).build();

    @BeforeEach
    void setUp() {
        service = new HoldingsServiceImpl(holdingsRepo, marginService, mongoTemplate,
                marketDataQuery, brokerMarginPolicyFactory, holdingsDataRedisRepo);
    }

    private Holdings.HoldingDetail detail(int id, int qty) {
        return Holdings.HoldingDetail.builder()
                .id(id).quantity(qty).price(new BigDecimal("100")).buyDate(java.time.Instant.EPOCH).build();
    }

    private Holdings holdingsWith(String symbol, Holdings.HoldingDetail... details) {
        var info = Holdings.HoldingInfo.builder()
                .symbol(symbol).margin(4.5f).ltp(new BigDecimal("100"))
                .holdingDetails(new CopyOnWriteArrayList<>(List.of(details)))
                .build();
        var holdings = Holdings.builder().userId(7L).build();
        holdings.getBrokerHoldingMap().put(BrokerType.ZERODHA, new CopyOnWriteArrayList<>(List.of(info)));
        return holdings;
    }

    private HoldingDto dto(String symbol, int detailId) {
        return HoldingDto.builder()
                .symbol(symbol)
                .holdingDetails(List.of(HoldingDto.HoldingDetailDto.builder()
                        .id(detailId).quantity(5).price(new BigDecimal("120")).buyDate("2026-08-15").build()))
                .build();
    }

    // --- reads ------------------------------------------------------------

    @Test
    void getAllHoldings_servesFromRedisWithoutTouchingMongo() {
        when(holdingsDataRedisRepo.get("7")).thenReturn(holdingsWith("TCS", detail(1, 5)));

        assertEquals(1, service.getAllHoldings(BrokerType.ZERODHA, USER).size());

        verify(holdingsRepo, never()).findById(any());
    }

    @Test
    void getAllHoldings_loadsAndCachesOnAMiss() {
        when(holdingsDataRedisRepo.get("7")).thenReturn(null);
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdingsWith("TCS", detail(1, 5))));

        service.getAllHoldings(BrokerType.ZERODHA, USER);

        verify(holdingsDataRedisRepo).set(eq("7"), any(Holdings.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    void getAllHoldings_throwsWhenTheUserHasNothingWithThatBroker() {
        when(holdingsDataRedisRepo.get("7")).thenReturn(holdingsWith("TCS", detail(1, 5)));

        assertThrows(NotFoundException.class, () -> service.getAllHoldings(BrokerType.RUPEEZY, USER));
    }

    @Test
    void getAllHoldings_throwsWhenTheUserHasNoHoldingsDocument() {
        when(holdingsDataRedisRepo.get("7")).thenReturn(null);
        when(holdingsRepo.findById(7L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getAllHoldings(BrokerType.ZERODHA, USER));
    }

    // --- create -----------------------------------------------------------

    @Test
    void createHoldings_appendsToAnExistingSymbolAndAssignsSequentialIds() {
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdingsWith("TCS", detail(1, 5), detail(2, 3))));

        assertTrue(service.createHoldings(BrokerType.ZERODHA, USER, dto("TCS", 0)));

        verify(holdingsRepo).save(any(Holdings.class));
        // Ids continue from the last row so existing rows stay addressable.
        verify(holdingsDataRedisRepo).delete("7");
    }

    @Test
    void createHoldings_createsTheHoldingInfoForANewSymbol() {
        when(holdingsRepo.findById(7L)).thenReturn(Optional.empty());
        when(marginService.getMarginCache()).thenReturn(Map.of("INFY",
                Margin.builder().symbol("INFY").token("1594").requiredMargin(new BigDecimal("3.2")).build()));
        var ticker = new SmartApiLtpResponse.MarketTicker();
        ticker.setLtp(1500.0);
        when(marketDataQuery.getMarketTicker("1594")).thenReturn(ticker);
        stubZerodhaMarginPolicy();

        assertTrue(service.createHoldings(BrokerType.ZERODHA, USER, dto("INFY", 0)));

        verify(holdingsRepo).save(any(Holdings.class));
    }

    @Test
    void createHoldings_stillSavesWhenTheLtpLookupFails() {
        // A broker outage must not block the user from recording a position.
        when(holdingsRepo.findById(7L)).thenReturn(Optional.empty());
        when(marginService.getMarginCache()).thenReturn(Map.of("INFY",
                Margin.builder().symbol("INFY").token("1594").requiredMargin(new BigDecimal("3.2")).build()));
        when(marketDataQuery.getMarketTicker("1594")).thenThrow(new NotFoundException("Ltp not found"));
        stubZerodhaMarginPolicy();

        assertTrue(service.createHoldings(BrokerType.ZERODHA, USER, dto("INFY", 0)));

        verify(holdingsRepo).save(any(Holdings.class));
    }

    // --- update -----------------------------------------------------------

    @Test
    void updateHoldings_mutatesTheMatchingDetailRow() {
        var holdings = holdingsWith("TCS", detail(1, 5));
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdings));

        assertTrue(service.updateHoldings(BrokerType.ZERODHA, USER, dto("TCS", 1)));

        var updated = holdings.getBrokerHoldingMap().get(BrokerType.ZERODHA)
                .getFirst().getHoldingDetails().getFirst();
        assertEquals(5, updated.getQuantity());
        assertEquals(0, new BigDecimal("120").compareTo(updated.getPrice()));
        verify(holdingsDataRedisRepo).delete("7");
    }

    @Test
    void updateHoldings_rejectsARequestWithoutADetailId() {
        // Id 0 means "new row"; updating without one would silently edit the wrong lot.
        assertThrows(BadRequestException.class,
                () -> service.updateHoldings(BrokerType.ZERODHA, USER, dto("TCS", 0)));
    }

    @Test
    void updateHoldings_throwsWhenTheSymbolOrRowIsUnknown() {
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdingsWith("TCS", detail(1, 5))));

        assertThrows(BadRequestException.class,
                () -> service.updateHoldings(BrokerType.ZERODHA, USER, dto("INFY", 1)));
        assertThrows(BadRequestException.class,
                () -> service.updateHoldings(BrokerType.ZERODHA, USER, dto("TCS", 99)));
    }

    @Test
    void updateHoldings_throwsWhenTheBrokerHasNoHoldings() {
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdingsWith("TCS", detail(1, 5))));

        assertThrows(BadRequestException.class,
                () -> service.updateHoldings(BrokerType.RUPEEZY, USER, dto("TCS", 1)));
    }

    // --- delete -----------------------------------------------------------

    @Test
    void deleteHoldings_pullsTheSymbolAndInvalidatesTheCache() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Holdings.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertTrue(service.deleteHoldings(BrokerType.ZERODHA, USER, "TCS"));

        verify(holdingsDataRedisRepo).delete("7");
    }

    @Test
    void deleteHoldings_throwsWhenNothingMatched() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Holdings.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThrows(NotFoundException.class,
                () -> service.deleteHoldings(BrokerType.ZERODHA, USER, "TCS"));
        verify(holdingsDataRedisRepo, never()).delete("7");
    }

    @Test
    void deleteHoldingDetail_removesOnlyTheRequestedRow() {
        var holdings = holdingsWith("TCS", detail(1, 5), detail(2, 3));
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdings));

        assertTrue(service.deleteHoldingDetail(BrokerType.ZERODHA, USER, "TCS", 1));

        var rows = holdings.getBrokerHoldingMap().get(BrokerType.ZERODHA).getFirst().getHoldingDetails();
        assertEquals(1, rows.size());
        assertEquals(2, rows.getFirst().getId());
    }

    @Test
    void deleteHoldingDetail_throwsWhenTheRowDoesNotExist() {
        when(holdingsRepo.findById(7L)).thenReturn(Optional.of(holdingsWith("TCS", detail(1, 5))));

        assertThrows(BadRequestException.class,
                () -> service.deleteHoldingDetail(BrokerType.ZERODHA, USER, "TCS", 99));
        verify(holdingsRepo, never()).save(any(Holdings.class));
    }

    // --- portfolio refresh ------------------------------------------------

}
