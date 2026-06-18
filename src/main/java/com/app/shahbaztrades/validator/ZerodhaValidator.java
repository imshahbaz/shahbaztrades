package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.model.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)

public class ZerodhaValidator {

    public static boolean validateZerodhaConfig(User.ZerodhaConfig config) {
        return config != null && !StringUtils.isAnyEmpty(config.getApiKey(), config.getApiSecret());
    }
}
