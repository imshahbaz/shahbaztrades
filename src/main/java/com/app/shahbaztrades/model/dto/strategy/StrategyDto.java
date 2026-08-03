package com.app.shahbaztrades.model.dto.strategy;

import com.app.shahbaztrades.model.entity.Strategy;
import com.app.shahbaztrades.model.enums.TimeFrame;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrategyDto {

    @NotBlank(message = "Strategy name is required")
    String name;

    @NotBlank(message = "Scan clause is required")
    String scanClause;

    boolean active;

    float successRate;

    @NotNull
    TimeFrame timeFrame;

    public Strategy toEntity() {
        this.setName(this.getName().toUpperCase());
        return Strategy.builder().name(this.name).scanClause(this.scanClause).active(this.active).timeFrame(this.timeFrame).build();
    }

}