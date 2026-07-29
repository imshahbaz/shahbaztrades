package com.app.shahbaztrades.model.entity.redis;

import com.app.shahbaztrades.model.dto.angelone.SmartApiLtpResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@RedisHash("angel_one_historical_data")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AngelOneHistoricalDataRedis {

    @Id
    String id;

    List<SmartApiLtpResponse.CandleDetail> dailyHistoricalData;

    List<SmartApiLtpResponse.CandleDetail> fifteenMinuteHistoricalData;

    @TimeToLive(unit = TimeUnit.SECONDS)
    Long ttl;
}
