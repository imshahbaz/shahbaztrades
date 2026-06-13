package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.model.entity.User;
import org.apache.commons.lang3.StringUtils;

public class ZerodhaValidator {

    public static boolean validateZerodhaConfig(User.ZerodhaConfig config) {
        return config != null && !StringUtils.isAnyEmpty(config.getApiKey(), config.getApiSecret());
    }
}
