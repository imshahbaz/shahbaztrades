package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import com.app.shahbaztrades.repo.MongoConfigsRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.NoSuchElementException;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoConfigServiceImpl implements MongoConfigService {

    private final MongoConfigsRepo mongoConfigsRepo;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private MongoEnvConfig cachedConfig;
    private MongoEnvConfig clientConfig;

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
        var id = Objects.equals(environment.getProperty("ENV"), "production") ? "mongoConfig" : "mongoConfigDev";
        this.cachedConfig = mongoConfigsRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Configuration not found in MongoDB"));
        log.info("Mongo configuration loaded successfully");
    }

    @Override
    public void refreshClientConfig() {
        var id = Objects.equals(environment.getProperty("ENV"), "production") ? "clientConfigId" : "clientConfigIdDev";
        this.clientConfig = mongoConfigsRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Configuration not found in MongoDB"));
        log.info("Client configuration loaded successfully");
    }

    @Override
    public MongoEnvConfig getConfig() {
        return this.cachedConfig;
    }

    @Override
    public MongoEnvConfig getClientConfig() {
        return this.clientConfig;
    }

    @Override
    public void updatePartialConfig(String configId, Map<String, Object> request) {
        try {
            objectMapper.convertValue(request, MongoEnvConfig.class);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid field provided in update request");
        }

        Query query = new Query(Criteria.where("_id").is(configId));
        Update update = new Update();
        request.forEach(update::set);
        var result = mongoTemplate.updateFirst(query, update, MongoEnvConfig.class);
        if (result.getModifiedCount() == 0) {
            throw new BadRequestException("No new update found");
        }
    }

}