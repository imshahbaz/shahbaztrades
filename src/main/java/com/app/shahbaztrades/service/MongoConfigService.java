package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.entity.MongoEnvConfig;

import java.util.Map;

public interface MongoConfigService {
    String getAngelOneJwtToken();

    void setAngelOneJwtToken(String angelOneJwtToken);

    String getAngelOneFeedToken();

    void setAngelOneFeedToken(String angelOneFeedToken);

    void refreshConfig();

    void refreshClientConfig();

    MongoEnvConfig getConfig();

    MongoEnvConfig getClientConfig();

    void updatePartialConfig(String configId, Map<String, Object> request);
}
