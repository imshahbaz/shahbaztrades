package com.shahbaz.trades.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class SessionConfig {

    private final MongoIndexedSessionRepository repository;

    public SessionConfig(MongoIndexedSessionRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void customizeSessionRepository() {
        repository.setDefaultMaxInactiveInterval(
                Duration.of(7, ChronoUnit.DAYS)
        );
        repository.setCollectionName("sessions");
    }

}
