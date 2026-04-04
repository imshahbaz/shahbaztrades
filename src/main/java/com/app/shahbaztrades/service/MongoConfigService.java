package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.entity.MongoEnvConfig;

public interface MongoConfigService {
    void refreshConfig();

    void refreshClientConfig();

    MongoEnvConfig getConfig();

    MongoEnvConfig getClientConfig();
}
