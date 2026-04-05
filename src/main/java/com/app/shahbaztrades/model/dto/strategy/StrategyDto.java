package com.app.shahbaztrades.model.dto.strategy;

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
}