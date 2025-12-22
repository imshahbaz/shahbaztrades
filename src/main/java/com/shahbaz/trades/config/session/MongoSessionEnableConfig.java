package com.shahbaz.trades.config.session;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.mongo.config.annotation.web.http.EnableMongoHttpSession;

@Configuration
@EnableMongoHttpSession
public class MongoSessionEnableConfig {
}
