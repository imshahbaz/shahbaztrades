package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.entity.MongoEnvConfig;

public interface MongoConfigService {
    String getAngelOneJwtToken();

    void setAngelOneJwtToken(String angelOneJwtToken);

    String getAngelOneFeedToken();

    void setAngelOneFeedToken(String angelOneFeedToken);

    void refreshConfig();

    void refreshClientConfig();

    MongoEnvConfig getConfig();

    MongoEnvConfig getClientConfig();
}
