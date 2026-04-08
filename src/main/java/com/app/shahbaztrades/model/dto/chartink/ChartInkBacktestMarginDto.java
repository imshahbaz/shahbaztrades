package com.app.shahbaztrades.model.dto.chartink;

import com.app.shahbaztrades.model.entity.Margin;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChartInkBacktestMarginDto {

    LocalDateTime marketTime;

    List<Margin> margins;
}
