package com.app.shahbaztrades.model.dto.rupeezy;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezyBaseResponse {
    String message;
    String status;
    String code;

    public boolean isSuccess() {
        return this.getStatus() != null && this.getStatus().equals("success");
    }
}
