package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserTheme;

import java.util.List;
import java.util.Set;

public interface UserService {

    User createUser(UserDto userDto);

    User findByUserIdOrEmailOrMobile(Long userId, String email, Long mobile);

    User findOrCreateGoogleUser(GoogleUser gUser);

    void updateUserName(UserDto userDto);

    UserTheme updateUserTheme(UserDto userDto);

    List<User> findByIds(Set<Long> userIds);

    /** @return false when no such user exists. */
    boolean updateZerodhaConfig(long userId, User.ZerodhaConfig config);

    /** @return false when no such user exists. */
    boolean updateRupeezyConfig(long userId, User.RupeezyConfig config);
}
