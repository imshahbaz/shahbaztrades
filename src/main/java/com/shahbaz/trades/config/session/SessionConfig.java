package com.shahbaz.trades.config.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class SessionConfig {

    private final MongoIndexedSessionRepository repository;

    public SessionConfig(MongoIndexedSessionRepository repository) {
        this.repository = repository;
        this.repository.setDefaultMaxInactiveInterval(
                Duration.of(5, ChronoUnit.MINUTES)
        );
        this.repository.setCollectionName("sessions");
    }

    public void deleteOldSessions(String email) {
        var existing = repository.findByIndexNameAndIndexValue(
                MongoIndexedSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                email);

        existing.keySet().forEach(repository::deleteById);
    }

}
