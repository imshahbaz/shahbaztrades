package com.shahbaz.trades.repository;

import com.shahbaz.trades.model.entity.Margin;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarginRepository extends MongoRepository<Margin, String> {
}
