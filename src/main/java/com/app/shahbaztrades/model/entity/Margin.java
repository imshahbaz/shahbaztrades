package com.app.shahbaztrades.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "margin")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Margin {

    @MongoId
    String symbol;

    String name;

    float margin;

    String token;
}