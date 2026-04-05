package com.app.shahbaztrades.model.entity;

import com.app.shahbaztrades.model.dto.strategy.StrategyDto;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chartink_strategy")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Strategy {

    @MongoId
    String name;

    String scanClause;

    boolean active;

    public StrategyDto toDto() {
        return StrategyDto.builder()
                .name(this.name)
                .scanClause(this.scanClause)
                .active(this.active)
                .build();
    }

}