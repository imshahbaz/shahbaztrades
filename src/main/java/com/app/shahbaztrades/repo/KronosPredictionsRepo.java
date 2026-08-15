package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.KronosPredictions;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KronosPredictionsRepo extends MongoRepository<KronosPredictions, String> {

    Optional<KronosPredictions> findBySymbol(String symbol, Pageable pageable);
}
