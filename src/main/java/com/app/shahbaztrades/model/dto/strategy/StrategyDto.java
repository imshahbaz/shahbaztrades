package com.app.shahbaztrades.model.dto.strategy;

import com.app.shahbaztrades.model.entity.Strategy;
import jakarta.validation.constraints.NotBlank;
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

    public Strategy toEntity() {
        this.setName(this.getName().toUpperCase());
        return Strategy.builder().name(name).scanClause(scanClause).active(active).build();
    }

}