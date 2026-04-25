package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.StrategyOrder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyOrderRepo extends MongoRepository<StrategyOrder, String> {
}
