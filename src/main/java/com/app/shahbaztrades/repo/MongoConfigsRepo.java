package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.MongoEnvConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoConfigsRepo extends MongoRepository<MongoEnvConfig, String> {
}
