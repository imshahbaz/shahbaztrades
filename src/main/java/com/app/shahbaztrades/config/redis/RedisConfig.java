package com.app.shahbaztrades.config.redis;

import com.app.shahbaztrades.service.MongoConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final MongoConfigService mongoConfigService;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${REDIS_HOST}") String host,
            @Value("${REDIS_PORT}") int port,
            @Value("${REDIS_USER}") String user,
            @Value("${REDIS_PASS}") String pass) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setUsername(user);
        config.setPassword(pass);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
}