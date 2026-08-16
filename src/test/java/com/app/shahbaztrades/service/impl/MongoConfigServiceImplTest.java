package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.entity.ClientConfigurations;
import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.model.enums.ConfigurationType;
import com.app.shahbaztrades.model.enums.Environments;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Map;

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
    private Environment environment;
    @Mock
    private MongoTemplate mongoTemplate;

    private MongoConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MongoConfigServiceImpl(environment, JsonMapper.builder().build(), mongoTemplate);
    }

    private ServerConfigurations serverConfig(Environments env) {
        var config = new ServerConfigurations();
        config.setId("server-" + env);
        config.setEnvironment(env);
        return config;
    }

    private ClientConfigurations clientConfig(Environments env) {
        var config = new ClientConfigurations();
        config.setId("client-" + env);
        config.setEnvironment(env);
        return config;
    }

    private Object queriedEnvironment(Query query) {
        return query.getQueryObject().get(ServerConfigurations.Fields.environment);
    }

    @Test
    void refreshConfig_readsTheProductionDocumentWhenEnvIsProduction() {
        when(environment.getProperty("ENV")).thenReturn(Environments.PRODUCTION.name());
        when(mongoTemplate.findOne(any(Query.class), eq(ServerConfigurations.class)))
                .thenReturn(serverConfig(Environments.PRODUCTION));

        service.refreshConfig();

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(ServerConfigurations.class));
        assertEquals(Environments.PRODUCTION, queriedEnvironment(query.getValue()));
        assertEquals("server-PRODUCTION", service.getConfig().getId());
    }

    @Test
    void refreshConfig_fallsBackToTheDevDocumentForAnyOtherEnv() {
        // A missing or misspelled ENV must never accidentally load production credentials.
        when(environment.getProperty("ENV")).thenReturn(null);
        when(mongoTemplate.findOne(any(Query.class), eq(ServerConfigurations.class)))
                .thenReturn(serverConfig(Environments.DEVELOPMENT));

        service.refreshConfig();

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(ServerConfigurations.class));
        assertEquals(Environments.DEVELOPMENT, queriedEnvironment(query.getValue()));
        assertEquals("server-DEVELOPMENT", service.getConfig().getId());
    }

    @Test
    void refreshConfig_failsFastWhenTheDocumentIsMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(ServerConfigurations.class))).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.refreshConfig());
    }

    @Test
    void refreshClientConfig_readsTheClientCollection() {
        when(environment.getProperty("ENV")).thenReturn(Environments.PRODUCTION.name());
        when(mongoTemplate.findOne(any(Query.class), eq(ClientConfigurations.class)))
                .thenReturn(clientConfig(Environments.PRODUCTION));

        service.refreshClientConfig();

        assertEquals("client-PRODUCTION", service.getClientConfig().getId());
    }

    @Test
    void refreshClientConfig_failsFastWhenTheDocumentIsMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(ClientConfigurations.class))).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.refreshClientConfig());
    }

    @Test
    void getConfigAndGetClientConfig_returnIndependentCaches() {
        when(mongoTemplate.findOne(any(Query.class), eq(ServerConfigurations.class)))
                .thenReturn(serverConfig(Environments.DEVELOPMENT));
        when(mongoTemplate.findOne(any(Query.class), eq(ClientConfigurations.class)))
                .thenReturn(clientConfig(Environments.DEVELOPMENT));

        service.refreshConfig();
        service.refreshClientConfig();

        assertEquals("server-DEVELOPMENT", service.getConfig().getId());
        assertEquals("client-DEVELOPMENT", service.getClientConfig().getId());
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
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ServerConfigurations.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.updatePartialConfig("server-DEVELOPMENT", ConfigurationType.SERVER,
                Map.of("leverage", 4.0, "jwtSecret", "s"));

        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(ServerConfigurations.class));
        var setDocument = update.getValue().getUpdateObject().get("$set", org.bson.Document.class);
        assertEquals(4.0, setDocument.get("leverage"));
        assertEquals("s", setDocument.get("jwtSecret"));
    }

    @Test
    void updatePartialConfig_routesToTheClientCollectionForClientUpdates() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ClientConfigurations.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.updatePartialConfig("client-DEVELOPMENT", ConfigurationType.CLIENT,
                Map.of("components", Map.of("heatMap", true)));

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(ClientConfigurations.class));
    }

    @Test
    void updatePartialConfig_rejectsFieldsThatDoNotExistOnTheConfig() {
        // Guards against silently writing a typo'd key that nothing ever reads back.
        assertThrows(BadRequestException.class, () -> service.updatePartialConfig(
                "server-DEVELOPMENT", ConfigurationType.SERVER, Map.of("leverage", "not-a-float")));
    }

    @Test
    void updatePartialConfig_reportsWhenNothingChanged() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ServerConfigurations.class)))
                .thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThrows(BadRequestException.class, () -> service.updatePartialConfig(
                "server-DEVELOPMENT", ConfigurationType.SERVER, Map.of("jwtSecret", "same")));
    }

    @Test
    void init_loadsBothConfigsOnStartup() {
        var server = serverConfig(Environments.DEVELOPMENT);
        var client = clientConfig(Environments.DEVELOPMENT);
        when(mongoTemplate.findOne(any(Query.class), eq(ServerConfigurations.class))).thenReturn(server);
        when(mongoTemplate.findOne(any(Query.class), eq(ClientConfigurations.class))).thenReturn(client);

        service.init();

        assertSame(server, service.getConfig());
        assertSame(client, service.getClientConfig());
    }
}
