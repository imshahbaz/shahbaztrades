package com.app.shahbaztrades.repo.redis;

import com.app.shahbaztrades.model.entity.redis.AngelOneHistoricalDataRedis;
import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AngelOneHistoricalDataRedisRepo extends KeyValueRepository<AngelOneHistoricalDataRedis, String> {
}
