package com.app.shahbaztrades.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Document(collection = "margin")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Margin {

    @MongoId
    String symbol;

    String name;

    @Field("margin")
    BigDecimal requiredMargin;

    String token;

    BigDecimal rupeezyMargin;
}