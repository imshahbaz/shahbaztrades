package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.service.MarginService;
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
class MarginSyncServiceImplTest {

    @Mock
    private MarginRepo marginRepo;
    @Mock
    private MarginService marginService;
    @Mock
    private MongoConfigService mongoConfigService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AngelOneClient angelOneClient;
    @Mock
    private RupeezyWebClient rupeezyWebClient;

    private MarginSyncServiceImpl service;

    private Margin margin(String symbol, String leverage) {
        return Margin.builder().symbol(symbol).name(symbol).token("t-" + symbol)
                .requiredMargin(new BigDecimal(leverage)).build();
    }

    @BeforeEach
    void setUp() {
        service = new MarginSyncServiceImpl(marginRepo, marginService, mongoConfigService,
                JsonMapper.builder().build(), mongoTemplate, angelOneClient, rupeezyWebClient);
    }

    @Test
    void syncAngelOneToken_persistsAndRefreshesWhenTokensAreReturned() {
        List<Margin> resolved = List.of(margin("TCS", "4.5"));
        when(angelOneClient.getTokens(anyMap())).thenReturn(resolved);

        service.syncAngelOneToken();

        verify(marginRepo).saveAll(resolved);
        // The cache must be rebuilt afterwards, otherwise the new tokens stay invisible in memory.
        verify(marginService).refreshMargins();
    }

    @Test
    void syncAngelOneToken_isANoOpWhenTheScripMasterReturnsNothing() {
        // A failed scrip-master download must not wipe the existing token mapping.
        when(angelOneClient.getTokens(anyMap())).thenReturn(List.of());

        service.syncAngelOneToken();

        verify(marginRepo, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
