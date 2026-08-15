package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.enums.Environments;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "client_configs")
public class ClientConfigurations {

    @MongoId
    String id;

    Environments environment;

    Auth auth;

    Components components;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auth {
        private boolean google;
        private boolean email;
        private boolean trueCaller;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Components {
        private boolean heatMap;
    }
}
