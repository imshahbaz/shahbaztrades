package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.repo.StrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceImplTest {

    @Mock
    private StrategyRepository strategyRepository;

    private StrategyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyServiceImpl(strategyRepository);
    }

    @Test
    void refreshStrategyCache_keysByUpperCaseNameRegardlessOfStoredCase() {
        when(strategyRepository.findAll()).thenReturn(List.of(
                Strategy.builder().name("myStrat").scanClause("x").active(true).build()
        ));

        service.refreshStrategyCache();

        // Look-ups happen by upper-case name; a mixed-case stored name must still resolve.
        assertNotNull(service.getCachedStrategies().get("MYSTRAT"),
                "cache must be keyed by canonical upper-case name");
    }

    @Test
    void refreshStrategyCache_rebuildsFromSourceSoDeletionsAreReflected() {
        when(strategyRepository.findAll())
                .thenReturn(List.of(Strategy.builder().name("A").scanClause("x").active(true).build()))
                .thenReturn(List.of());

        service.refreshStrategyCache();
        assertTrue(service.getCachedStrategies().containsKey("A"));

        // Second refresh returns nothing: the cache must clear, not retain the stale entry.
        service.refreshStrategyCache();
        assertNull(service.getCachedStrategies().get("A"));
    }
}
