package com.shahbaz.trades.repository;

import com.shahbaz.trades.model.entity.Margin;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarginRepository extends MongoRepository<Margin, String> {

    @Query(value = "{ '_id' : { '$nin' : ?0 } }", delete = true)
    long deleteByIdNotIn(List<String> ids);
}
