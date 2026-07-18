package com.app.shahbaztrades;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Integration test: requires live MONGO_URI/REDIS_* and other env vars. "
        + "Enable when running against real infrastructure.")
class TradeAppTests {

    @Test
    void contextLoads() {
        // Test context loads successfully
    }

}
