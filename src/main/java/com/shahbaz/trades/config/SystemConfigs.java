package com.shahbaz.trades.config;

import com.google.gson.Gson;
import com.shahbaz.trades.model.config.EnvConfig;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class SystemConfigs {

    @Value("${config}")
    private String json;

    private EnvConfig config;

    @PostConstruct
    public void init() {
        config = new Gson().fromJson(json, EnvConfig.class);
    }

}
