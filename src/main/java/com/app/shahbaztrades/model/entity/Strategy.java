package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Document(collection = "chartink_strategy")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Strategy {

    @MongoId
    String name;

    String scanClause;

    boolean active;

    float successRate;

    public StrategyDto toDto() {
        return StrategyDto.builder()
                .name(this.name)
                .scanClause(this.scanClause)
                .active(this.active)
                .successRate(this.successRate)
                .build();
    }

}