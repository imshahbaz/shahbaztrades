package com.shahbaz.trades.repository;

import com.shahbaz.trades.model.entity.Strategy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyRepository extends MongoRepository<Strategy, String> {
}
