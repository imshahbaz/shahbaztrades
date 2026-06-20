package com.app.shahbaztrades.model.dto.rupeezy;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.util.TotpUtil;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RupeezySessionRequest {
    String checksum;
    String applicationId;
    String token;

    public void addChecksum(String apiSecret) {
        if (StringUtils.isAnyEmpty(apiSecret, applicationId, token)) {
            throw new BadRequestException("data unavailable to create checksum");
        }
        this.checksum = TotpUtil.generateChecksum(applicationId, token, apiSecret);
    }
}
