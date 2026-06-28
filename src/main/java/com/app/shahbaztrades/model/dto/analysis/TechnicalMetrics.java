package com.app.shahbaztrades.model.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TechnicalMetrics {
    public double atrValue;
    public double expectedMovePercent;

    public boolean isAtrValid() {
        return atrValue > 0 && expectedMovePercent > 0;
    }
}
