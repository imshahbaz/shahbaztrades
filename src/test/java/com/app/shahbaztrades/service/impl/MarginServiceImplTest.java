package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.angelone.AngelOneClient;
import com.app.shahbaztrades.components.rupeezy.RupeezyWebClient;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.entity.Margin;
import com.app.shahbaztrades.repo.MarginRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarginServiceImplTest {

    @Mock
    private MarginRepo marginRepo;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AngelOneClient angelOneClient;
    @Mock
    private RupeezyWebClient rupeezyWebClient;

    private MarginServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarginServiceImpl(marginRepo, mongoConfigService, JsonMapper.builder().build(),
                mongoTemplate, angelOneClient, rupeezyWebClient);
    }

    private Margin margin(String symbol, String leverage) {
        return Margin.builder().symbol(symbol).name(symbol).token("t-" + symbol)
                .requiredMargin(new BigDecimal(leverage)).build();
    }

    @Test
    void refreshMargins_keysTheCacheBySymbol() {
        when(marginRepo.findAll()).thenReturn(List.of(margin("TCS", "4.5"), margin("INFY", "3.2")));

        service.refreshMargins();

        Map<String, Margin> cache = service.getMarginCache();
        assertEquals(2, cache.size());
        assertEquals("TCS", cache.get("TCS").getSymbol());
    }

    @Test
    void refreshMargins_rebuildsSoDelistedSymbolsDisappear() {
        when(marginRepo.findAll())
                .thenReturn(List.of(margin("TCS", "4.5")))
                .thenReturn(List.of(margin("INFY", "3.2")));

        service.refreshMargins();
        service.refreshMargins();

        // A stale TCS entry would let orders be sized against a leverage the broker no longer offers.
        assertTrue(service.getMarginCache().containsKey("INFY"));
        assertEquals(1, service.getMarginCache().size());
        assertEquals(null, service.getMarginCache().get("TCS"));
    }

    @Test
    void getMargin_returnsTheCachedEntry() {
        when(marginRepo.findAll()).thenReturn(List.of(margin("TCS", "4.5")));
        service.refreshMargins();

        assertEquals(0, new BigDecimal("4.5").compareTo(service.getMargin("TCS").getRequiredMargin()));
    }

    @Test
    void getMargin_throwsForAnUnknownSymbol() {
        when(marginRepo.findAll()).thenReturn(List.of());
        service.refreshMargins();

        assertThrows(NotFoundException.class, () -> service.getMargin("NOPE"));
    }

    @Test
    void getAllMargins_returnsEveryCachedValue() {
        when(marginRepo.findAll()).thenReturn(List.of(margin("TCS", "4.5"), margin("INFY", "3.2")));
        service.refreshMargins();

        assertEquals(2, service.getAllMargins().size());
    }

    @Test
    void syncAngelOneToken_persistsAndRefreshesWhenTokensAreReturned() {
        List<Margin> resolved = List.of(margin("TCS", "4.5"));
        when(angelOneClient.getTokens(anyMap())).thenReturn(resolved);
        when(marginRepo.findAll()).thenReturn(resolved);

        service.syncAngelOneToken();

        verify(marginRepo).saveAll(resolved);
        // The cache must be rebuilt afterwards, otherwise the new tokens stay invisible in memory.
        assertEquals("t-TCS", service.getMarginCache().get("TCS").getToken());
    }

    @Test
    void syncAngelOneToken_isANoOpWhenTheScripMasterReturnsNothing() {
        // A failed scrip-master download must not wipe the existing token mapping.
        when(angelOneClient.getTokens(anyMap())).thenReturn(List.of());

        service.syncAngelOneToken();

        verify(marginRepo, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void init_populatesTheCacheOnStartup() {
        when(marginRepo.findAll()).thenReturn(List.of(margin("TCS", "4.5")));

        service.init();

        assertEquals(1, service.getMarginCache().size());
    }
}
