package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.Margin;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarginRepo extends MongoRepository<Margin, String> {
}
