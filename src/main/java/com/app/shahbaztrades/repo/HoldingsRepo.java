package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.Holdings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HoldingsRepo extends MongoRepository<Holdings, Long> {
}
