package com.app.shahbaztrades.repo.redis;

import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AngelOneHistoricalDataRedisRepo extends CrudRepository<AngelOneHistoricalDataRedis, String> {
}
