package com.app.shahbaztrades.model.dto.angelone.websocket;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AngelOneWsSubscribeDto {
    @NotEmpty
    List<String> tokens;

    @Min(1)
    int exchangeType;
}