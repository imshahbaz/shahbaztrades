package com.shahbaz.trades.model.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EnvConfig {
    String brevoEmail;
    String brevoApiKey;
    String apiKey;
    String mongoUser;
    String mongoPassword;
}
