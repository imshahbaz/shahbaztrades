package com.app.shahbaztrades.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Document(collection = "counters")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DatabaseCounter {

    @MongoId
    String id;
    long seq;
}