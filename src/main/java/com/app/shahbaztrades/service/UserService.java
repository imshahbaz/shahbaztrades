package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserTheme;

import java.util.List;
import java.util.Set;

public interface UserService {

    String USER_ID_SEQ = "userid";

    User createUser(UserDto userDto);

    User findByUserIdOrEmailOrMobile(Long userId, String email, Long mobile);

    User findOrCreateGoogleUser(GoogleUser gUser);

    void updateUserName(UserDto userDto);

    UserTheme updateUserTheme(UserDto userDto);

    List<User> findByIds(Set<Long> userIds);
}
