package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.entity.ClientConfigurations;
import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.model.enums.ConfigurationType;

import java.util.Map;

public interface MongoConfigService {
    void refreshConfig();

    void refreshClientConfig();

    ServerConfigurations getConfig();

    ClientConfigurations getClientConfig();

    void updatePartialConfig(String configId, ConfigurationType configurationType, Map<String, Object> request);
}
