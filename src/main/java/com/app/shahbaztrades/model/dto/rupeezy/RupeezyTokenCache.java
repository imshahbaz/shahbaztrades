package com.app.shahbaztrades.model.dto.rupeezy;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyTokenCache {
    String apiSecret;
    String accessToken;
}
