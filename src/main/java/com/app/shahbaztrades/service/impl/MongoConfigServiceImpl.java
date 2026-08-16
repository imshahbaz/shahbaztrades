package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.model.entity.ClientConfigurations;
import com.app.shahbaztrades.model.entity.ServerConfigurations;
import com.app.shahbaztrades.model.enums.ConfigurationType;
import com.app.shahbaztrades.model.enums.Environments;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoConfigServiceImpl implements MongoConfigService {

    private final Environment environment;
    private final JsonMapper jsonMapper;
    private final MongoTemplate mongoTemplate;
    private ServerConfigurations backendConfigs;
    private ClientConfigurations clientConfigs;

    @Getter
    @Setter
    private String angelOneJwtToken;

    @Getter
    @Setter
    private String angelOneFeedToken;

    @PostConstruct
    public void init() {
        refreshConfig();
        refreshClientConfig();
    }

    @Override
    public void refreshConfig() {
        Environments env = Objects.equals(environment.getProperty("ENV"), Environments.PRODUCTION.name()) ? Environments.PRODUCTION : Environments.DEVELOPMENT;
        Query query = new Query(Criteria.where(ServerConfigurations.Fields.environment).is(env));
        var config = mongoTemplate.findOne(query, ServerConfigurations.class);
        if (config == null) {
            throw new NotFoundException("Backend configuration not found");
        }
        this.backendConfigs = config;
        log.info("Mongo configuration loaded successfully");
    }

    @Override
    public void refreshClientConfig() {
        Environments env = Objects.equals(environment.getProperty("ENV"), Environments.PRODUCTION.name()) ? Environments.PRODUCTION : Environments.DEVELOPMENT;
        Query query = new Query(Criteria.where(ServerConfigurations.Fields.environment).is(env));
        var config = mongoTemplate.findOne(query, ClientConfigurations.class);
        if (config == null) {
            throw new NotFoundException("Client configuration not found");
        }
        this.clientConfigs = config;
        log.info("Client configuration loaded successfully");
    }

    @Override
    public ServerConfigurations getConfig() {
        return this.backendConfigs;
    }

    @Override
    public ClientConfigurations getClientConfig() {
        return this.clientConfigs;
    }

    @Override
    public void updatePartialConfig(String configId, ConfigurationType configurationType, Map<String, Object> request) {
        try {
            jsonMapper.convertValue(request, configurationType.getConfigClassName());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid field provided in update request");
        }

        Query query = new Query(Criteria.where("_id").is(configId));
        Update update = new Update();
        request.forEach(update::set);
        var result = mongoTemplate.updateFirst(query, update, configurationType.getConfigClassName());
        if (result.getModifiedCount() == 0) {
            throw new BadRequestException("No new update found");
        }
    }

}