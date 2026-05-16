package com.app.shahbaztrades.model.dto.angelone;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record SmartApiLtpDto(String mode, Map<String, List<String>> exchangeTokens) {
}
