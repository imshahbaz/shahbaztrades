package com.app.shahbaztrades.model.dto.rupeezy;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyTokenCache implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    String apiSecret;
    String accessToken;
}
