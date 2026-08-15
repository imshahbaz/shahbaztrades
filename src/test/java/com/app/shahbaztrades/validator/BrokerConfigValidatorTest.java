package com.app.shahbaztrades.validator;

import com.app.shahbaztrades.model.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerConfigValidatorTest {

    @Test
    void validateZerodhaConfig_requiresBothKeyAndSecret() {
        assertTrue(BrokerConfigValidator.validateZerodhaConfig(zerodha("key", "secret")));
        assertFalse(BrokerConfigValidator.validateZerodhaConfig(zerodha("key", "")));
        assertFalse(BrokerConfigValidator.validateZerodhaConfig(zerodha("", "secret")));
        assertFalse(BrokerConfigValidator.validateZerodhaConfig(zerodha(null, "secret")));
    }

    @Test
    void validateZerodhaConfig_rejectsNullConfig() {
        // A user who never registered a broker has a null config; this must not NPE.
        assertFalse(BrokerConfigValidator.validateZerodhaConfig(null));
    }

    @Test
    void validateRupeezyConfig_requiresBothAppIdAndSecret() {
        assertTrue(BrokerConfigValidator.validateRupeezyConfig(rupeezy("app", "secret")));
        assertFalse(BrokerConfigValidator.validateRupeezyConfig(rupeezy("app", null)));
        assertFalse(BrokerConfigValidator.validateRupeezyConfig(rupeezy("", "secret")));
    }

    @Test
    void validateRupeezyConfig_rejectsNullConfig() {
        assertFalse(BrokerConfigValidator.validateRupeezyConfig(null));
    }

    @Test
    void isZerodhaAutoLoginEnabled_needsCredentialsAndTotp() {
        User user = User.builder().zerodhaConfig(zerodha("key", "secret")).build();
        assertFalse(user.isZerodhaAutoLoginEnabled(), "no TOTP secret -> no unattended login");

        var full = zerodha("key", "secret");
        full.setUserName("kite-user");
        full.setPassword("pw");
        full.setTotpSecret("JBSWY3DPEHPK3PXP");
        assertTrue(User.builder().zerodhaConfig(full).build().isZerodhaAutoLoginEnabled());
    }

    @Test
    void isZerodhaAutoLoginEnabled_isFalseWhenNoBrokerRegistered() {
        assertFalse(User.builder().build().isZerodhaAutoLoginEnabled());
    }

    private User.ZerodhaConfig zerodha(String apiKey, String apiSecret) {
        var config = new User.ZerodhaConfig();
        config.setApiKey(apiKey);
        config.setApiSecret(apiSecret);
        return config;
    }

    private User.RupeezyConfig rupeezy(String appId, String apiSecret) {
        var config = new User.RupeezyConfig();
        config.setAppId(appId);
        config.setApiSecret(apiSecret);
        return config;
    }
}
