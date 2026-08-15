package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.TimeFrame;
import com.app.shahbaztrades.repo.StrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRUD and filtering behaviour of StrategyServiceImpl, complementing StrategyServiceImplTest. */
@ExtendWith(MockitoExtension.class)
class StrategyServiceImplCrudTest {

    @Mock
    private StrategyRepository strategyRepository;

    private StrategyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyServiceImpl(strategyRepository);
    }

    private Strategy strategy(String name, boolean active, TimeFrame timeFrame) {
        return Strategy.builder().name(name).scanClause("close > 100").active(active).timeFrame(timeFrame).build();
    }

    private StrategyDto dto(String name, TimeFrame timeFrame) {
        return StrategyDto.builder()
                .name(name).scanClause("close > 100").active(true).timeFrame(timeFrame).build();
    }

    @Test
    void getAllStrategies_returnsOnlyActiveStrategiesForTheRequestedTimeframe() {
        when(strategyRepository.findAll()).thenReturn(List.of(
                strategy("DAILY_ON", true, TimeFrame.DAILY),
                strategy("DAILY_OFF", false, TimeFrame.DAILY),
                strategy("INTRADAY_ON", true, TimeFrame.FIFTEEN_MINUTE)));
        service.refreshStrategyCache();

        var daily = service.getAllStrategies(TimeFrame.DAILY);

        assertEquals(1, daily.size());
        assertEquals("DAILY_ON", daily.getFirst().getName());
    }

    @Test
    void getAllStrategiesAdmin_returnsEverythingRegardlessOfStateOrTimeframe() {
        when(strategyRepository.findAll()).thenReturn(List.of(
                strategy("A", true, TimeFrame.DAILY),
                strategy("B", false, TimeFrame.FIFTEEN_MINUTE)));
        service.refreshStrategyCache();

        assertEquals(2, service.getAllStrategiesAdmin().size());
    }

    @Test
    void createStrategy_insertsAndCachesUnderTheUpperCaseName() {
        var created = service.createStrategy(dto("myStrat", TimeFrame.DAILY));

        verify(strategyRepository).insert(any(Strategy.class));
        assertNotNull(service.getCachedStrategies().get("MYSTRAT"));
        assertEquals("MYSTRAT", created.getName());
    }

    @Test
    void createStrategy_translatesADuplicateKeyIntoAConflict() {
        when(strategyRepository.insert(any(Strategy.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.createStrategy(dto("MYSTRAT", TimeFrame.DAILY)));
        assertTrue(service.getCachedStrategies().isEmpty(),
                "a failed insert must not leave the strategy visible in the cache");
    }

    @Test
    void updateStrategy_savesAndRefreshesTheCachedEntry() {
        var updated = dto("MYSTRAT", TimeFrame.FIFTEEN_MINUTE);

        service.updateStrategy(updated);

        verify(strategyRepository).save(any(Strategy.class));
        assertEquals(TimeFrame.FIFTEEN_MINUTE, service.getCachedStrategies().get("MYSTRAT").getTimeFrame());
    }

    @Test
    void deleteStrategy_removesTheRowAndRebuildsTheCache() {
        when(strategyRepository.findAll())
                .thenReturn(List.of(strategy("A", true, TimeFrame.DAILY)))
                .thenReturn(List.of());
        service.refreshStrategyCache();

        service.deleteStrategy("A");

        verify(strategyRepository).deleteById("A");
        assertTrue(service.getCachedStrategies().isEmpty());
    }

    @Test
    void init_warmsTheCacheOnStartup() {
        when(strategyRepository.findAll()).thenReturn(List.of(strategy("A", true, TimeFrame.DAILY)));

        service.init();

        assertNotNull(service.getCachedStrategies().get("A"));
    }
}
