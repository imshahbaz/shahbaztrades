package com.app.shahbaztrades.model.dto.kronos;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkPredictionRequestDto {

    String model;

    List<PredictionItemDto> predictions;
}