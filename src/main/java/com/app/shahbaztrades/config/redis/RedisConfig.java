package com.app.shahbaztrades.config.redis;

import com.app.shahbaztrades.service.MongoConfigService;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

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

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .keepAlive(true)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(5))
                .useSsl()
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
}