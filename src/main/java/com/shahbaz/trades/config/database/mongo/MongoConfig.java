package com.shahbaz.trades.config.database.mongo;

import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.lang.NonNull;
import com.shahbaz.trades.config.env.SystemConfigs;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Collection;
import java.util.Collections;

@Configuration
@RequiredArgsConstructor
@EnableMongoRepositories(basePackages = "com.shahbaz.trades.repository")
public class MongoConfig extends AbstractMongoClientConfiguration {

    private final SystemConfigs systemConfigs;

    @NonNull
    @Override
    protected String getDatabaseName() {
        return "ShahbazTrades";
    }

    @NonNull
    @Override
    public MongoClient mongoClient() {
        String rawString = "mongodb+srv://%s:%s@jaguartrading.ptkr6fq.mongodb.net/ShahbazTrades";
        ConnectionString connectionString = new ConnectionString(
                String.format(rawString, systemConfigs.getConfig().getMongoUser(),
                        systemConfigs.getConfig().getMongoPassword()));
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(mongoClientSettings);
    }

    @NonNull
    @Override
    protected Collection<String> getMappingBasePackages() {
        return Collections.singleton("com.shahbaz.trades.model.entity");
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }
}
