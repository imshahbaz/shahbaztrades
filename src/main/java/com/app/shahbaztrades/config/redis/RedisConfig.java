package com.app.shahbaztrades.config.redis;

import com.app.shahbaztrades.service.MongoConfigService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RedissonClient;
import org.redisson.api.WorkerOptions;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.ConstantDelay;
import org.redisson.executor.SpringTasksInjector;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private static final String SCHEDULER_NAME = "1Klik-Scheduler";
    private final MongoConfigService mongoConfigService;
    private final ForyRedisSerializer<?> foryRedisSerializer;

    @Bean
    public RedissonConnectionFactory redisConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedissonConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedissonClient redissonClient(JsonMapper jsonMapper) {

        Config config = new Config();
        config.setCodec(new JsonJacksonCodec(jsonMapper));

        config.useSingleServer()
                .setAddress(mongoConfigService.getConfig().getRedisUrl())
                .setConnectTimeout((int) Duration.ofSeconds(10).toMillis())
                .setTimeout((int) Duration.ofSeconds(5).toMillis())
                .setRetryAttempts(5)
                .setRetryDelay(new ConstantDelay(Duration.ofSeconds(2)))
                .setConnectionMinimumIdleSize(3)
                .setConnectionPoolSize(10)
                .setSubscriptionConnectionMinimumIdleSize(2)
                .setSubscriptionConnectionPoolSize(5);

        return Redisson.create(config);
    }

    @Bean(destroyMethod = "")
    public RScheduledExecutorService rScheduledExecutorService(RedissonClient redissonClient, BeanFactory beanFactory) {
        RScheduledExecutorService executorService = redissonClient.getExecutorService(SCHEDULER_NAME);
        executorService.deregisterWorkers();
        WorkerOptions options = WorkerOptions.defaults()
                .workers(3)
                .tasksInjector(new SpringTasksInjector(beanFactory));

        executorService.registerWorkers(options);
        return executorService;
    }

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> configMap = new ConcurrentHashMap<>();

        CacheConfig authCacheConfig = new CacheConfig(
                Duration.ofSeconds(1).toMillis(),
                Duration.ofSeconds(1).toMillis()
        );

        configMap.put("zerodhaAuthCache", authCacheConfig);
        return new RedissonSpringCacheManager(redissonClient, configMap);
    }

    @Bean("redisTemplateObject")
    public <T> RedisTemplate<String, T> redisTemplateObject(RedissonConnectionFactory factory) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(foryRedisSerializer);
        template.setHashValueSerializer(foryRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

}