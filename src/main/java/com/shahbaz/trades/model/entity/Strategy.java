package com.shahbaz.trades.model.entity;

import com.shahbaz.trades.model.dto.StrategyDto;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "chartink_strategy")
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
