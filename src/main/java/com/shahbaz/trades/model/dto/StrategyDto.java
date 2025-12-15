package com.shahbaz.trades.model.dto;

import com.shahbaz.trades.model.entity.Strategy;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrategyDto {

    @NotBlank
    String name;

    @NotBlank
    String scanClause;

    boolean active;

    public Strategy toEntity() {
        return Strategy.builder()
                .name(this.name.toUpperCase())
                .scanClause(this.scanClause)
                .active(this.active)
                .build();
    }

}
