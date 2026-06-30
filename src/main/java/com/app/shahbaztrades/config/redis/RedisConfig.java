package com.app.shahbaztrades.config.redis;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private static final String SCHEDULER_NAME = "1Klik-Scheduler";

    @Bean
    public RedissonConnectionFactory redisConnectionFactory(RedissonClient redissonClient) {
        return new RedissonConnectionFactory(redissonClient);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedissonConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedissonClient redissonClient(
            @Value("${REDIS_HOST}") String host,
            @Value("${REDIS_PORT}") int port,
            @Value("${REDIS_USER}") String user,
            @Value("${REDIS_PASS}") String pass) {

        Config config = new Config();
        config.setUsername(user);
        config.setPassword(pass);
        config.setCodec(new JsonJacksonCodec());

        String redisUrl = String.format("rediss://%s:%d", host, port);

        config.useSingleServer()
                .setAddress(redisUrl)
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

        CacheConfig authCacheConfig = new org.redisson.spring.cache.CacheConfig(
                Duration.ofSeconds(1).toMillis(),
                Duration.ofSeconds(1).toMillis()
        );

        configMap.put("zerodhaAuthCache", authCacheConfig);
        return new RedissonSpringCacheManager(redissonClient, configMap);
    }

}