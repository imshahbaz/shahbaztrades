package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import com.app.shahbaztrades.repo.MongoConfigsRepo;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoConfigServiceImplTest {

    @Mock
    private MongoConfigsRepo mongoConfigsRepo;
    @Mock
    private Environment environment;
    @Mock
    private MongoTemplate mongoTemplate;

    private MongoConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MongoConfigServiceImpl(mongoConfigsRepo, environment,
                JsonMapper.builder().build(), mongoTemplate);
    }

    private MongoEnvConfig config(String id) {
        var config = new MongoEnvConfig();
        config.setId(id);
        return config;
    }

    @Test
    void refreshConfig_readsTheProductionDocumentWhenEnvIsProduction() {
        when(environment.getProperty("ENV")).thenReturn("production");
        when(mongoConfigsRepo.findById("mongoConfig")).thenReturn(Optional.of(config("mongoConfig")));

        service.refreshConfig();

        assertEquals("mongoConfig", service.getConfig().getId());
    }

    @Test
    void refreshConfig_fallsBackToTheDevDocumentForAnyOtherEnv() {
        // A missing or misspelled ENV must never accidentally load production credentials.
        when(environment.getProperty("ENV")).thenReturn(null);
        when(mongoConfigsRepo.findById("mongoConfigDev")).thenReturn(Optional.of(config("mongoConfigDev")));

        service.refreshConfig();

        assertEquals("mongoConfigDev", service.getConfig().getId());
    }

    @Test
    void refreshConfig_failsFastWhenTheDocumentIsMissing() {
        when(mongoConfigsRepo.findById(any())).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.refreshConfig());
    }

    @Test
    void refreshClientConfig_usesItsOwnDocumentId() {
        when(environment.getProperty("ENV")).thenReturn("production");
        when(mongoConfigsRepo.findById("clientConfigId")).thenReturn(Optional.of(config("clientConfigId")));

        service.refreshClientConfig();

        assertEquals("clientConfigId", service.getClientConfig().getId());
    }

    @Test
    void getConfigAndGetClientConfig_returnIndependentCaches() {
        when(mongoConfigsRepo.findById("mongoConfigDev")).thenReturn(Optional.of(config("mongoConfigDev")));
        when(mongoConfigsRepo.findById("clientConfigIdDev")).thenReturn(Optional.of(config("clientConfigIdDev")));

        service.refreshConfig();
        service.refreshClientConfig();

        assertEquals("mongoConfigDev", service.getConfig().getId());
        assertEquals("clientConfigIdDev", service.getClientConfig().getId());
    }

    @Test
    void angelOneTokens_areHeldInMemoryAndReadBack() {
        service.setAngelOneJwtToken("jwt");
        service.setAngelOneFeedToken("feed");

        assertEquals("jwt", service.getAngelOneJwtToken());
        assertEquals("feed", service.getAngelOneFeedToken());
    }

    @Test
    void updatePartialConfig_appliesEveryRequestedField() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(MongoEnvConfig.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.updatePartialConfig("mongoConfig", Map.of("leverage", 4.0, "jwtSecret", "s"));

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(MongoEnvConfig.class));
    }

    @Test
    void updatePartialConfig_rejectsFieldsThatDoNotExistOnTheConfig() {
        // Guards against silently writing a typo'd key that nothing ever reads back.
        assertThrows(BadRequestException.class,
                () -> service.updatePartialConfig("mongoConfig", Map.of("leverage", "not-a-float")));
    }

    @Test
    void updatePartialConfig_reportsWhenNothingChanged() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(MongoEnvConfig.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThrows(BadRequestException.class,
                () -> service.updatePartialConfig("mongoConfig", Map.of("jwtSecret", "same")));
    }

    @Test
    void init_loadsBothConfigsOnStartup() {
        var main = config("mongoConfigDev");
        var client = config("clientConfigIdDev");
        when(mongoConfigsRepo.findById("mongoConfigDev")).thenReturn(Optional.of(main));
        when(mongoConfigsRepo.findById("clientConfigIdDev")).thenReturn(Optional.of(client));

        service.init();

        assertSame(main, service.getConfig());
        assertSame(client, service.getClientConfig());
    }
}
