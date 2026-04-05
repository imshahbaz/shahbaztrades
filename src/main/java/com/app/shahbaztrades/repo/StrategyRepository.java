package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.Strategy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyRepository extends MongoRepository<Strategy, String> {
}