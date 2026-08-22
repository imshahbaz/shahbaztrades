package com.app.shahbaztrades.model;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.util.AuthUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMappingTest {

    @Test
    void toEntity_hashesThePasswordRatherThanStoringIt() {
        User user = UserDto.builder().email("jane@example.com").password("s3cret").build().toEntity();

        assertNotEquals("s3cret", user.getPassword());
        assertTrue(AuthUtil.ENCODER.matches("s3cret", user.getPassword()));
    }

    @Test
    void toEntity_derivesUsernameFromTheEmailLocalPart() {
        User user = UserDto.builder().email("Jane.Doe@Example.com").password("x").build().toEntity();
        assertEquals("jane.doe", user.getUsername());
    }

    @Test
    void toEntity_fallsBackToTheFirstNameWhenThereIsNoEmail() {
        User user = UserDto.builder().name("Jane Doe").password("x").build().toEntity();

        // A random suffix is appended to keep the handle unique.
        assertTrue(user.getUsername().startsWith("jane"), "got: " + user.getUsername());
        assertTrue(user.getUsername().length() > "jane".length());
    }

    @Test
    void toEntity_leavesUsernameBlankWhenThereIsNothingToDeriveFrom() {
        assertEquals("", UserDto.builder().password("x").build().toEntity().getUsername());
    }

    @Test
    void toEntity_alwaysForcesTheUserRoleAndDarkTheme() {
        // Privilege escalation guard: a client-supplied ADMIN role must be ignored on signup.
        User user = UserDto.builder()
                .email("a@b.com").password("x").role(UserRole.ADMIN).theme(UserTheme.LIGHT)
                .build().toEntity();

        assertEquals(UserRole.USER, user.getRole());
        assertEquals(UserTheme.DARK, user.getTheme());
    }

    @Test
    void toDto_neverLeaksThePasswordHash() {
        User user = User.builder()
                .userId(3L).email("a@b.com").username("a").password("$2a$hash")
                .role(UserRole.USER).theme(UserTheme.DARK).name("A").profile("pic").mobile(99L)
                .build();

        UserDto dto = user.toDto();

        assertNull(dto.getPassword(), "the password hash must never reach a response body");
        assertEquals(3L, dto.getUserId());
        assertEquals("a@b.com", dto.getEmail());
        assertEquals(UserRole.USER, dto.getRole());
        assertEquals(99L, dto.getMobile());
    }

    @Test
    void toDto_doesNotCopyBrokerCredentials() {
        var zerodha = new User.ZerodhaConfig();
        zerodha.setApiKey("key");
        zerodha.setApiSecret("secret");

        UserDto dto = User.builder().userId(1L).zerodhaConfig(zerodha).build().toDto();

        // UserDto has no broker fields at all; this asserts the DTO stays credential-free.
        assertNull(dto.getPassword());
        assertEquals(1L, dto.getUserId());
    }

    @Test
    void authRequest_toUserDtoCarriesOnlyEmailAndPassword() {
        UserDto dto = new AuthRequest("a@b.com", "pw", "pw").toUserDto();

        assertEquals("a@b.com", dto.getEmail());
        assertEquals("pw", dto.getPassword());
        assertNull(dto.getRole());
    }

    @Test
    void zerodhaConfig_isTotpEnabledOnlyWhenAllThreeFieldsArePresent() {
        var config = new User.ZerodhaConfig();
        config.setTotpSecret("SEED");
        assertEquals(false, config.isTotpEnabled(), "a seed alone is not enough to log in");

        config.setUserName("kite");
        assertEquals(false, config.isTotpEnabled());

        config.setPassword("pw");
        assertEquals(true, config.isTotpEnabled());
    }
}
