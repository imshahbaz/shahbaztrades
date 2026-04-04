package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import com.app.shahbaztrades.repo.MongoConfigsRepo;
import com.app.shahbaztrades.service.MongoConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoConfigServiceImpl implements MongoConfigService {

    private final MongoConfigsRepo mongoConfigsRepo;
    private MongoEnvConfig cachedConfig;
    private MongoEnvConfig clientConfig;
    private final Environment environment;

    @PostConstruct
    public void init() {
        refreshConfig();
        refreshClientConfig();
    }

    @Override
    public void refreshConfig() {
        var id = Objects.equals(environment.getProperty("ENV"), "production") ? "mongoConfig":"mongoConfigDev";
        this.cachedConfig = mongoConfigsRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Configuration not found in MongoDB"));
        log.info("Mongo configuration loaded successfully");
    }

    @Override
    public void refreshClientConfig() {
        var id = Objects.equals(environment.getProperty("ENV"), "production") ? "clientConfigId":"clientConfigIdDev";
        this.clientConfig = mongoConfigsRepo.findById("clientConfigIdDev")
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

}