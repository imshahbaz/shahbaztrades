package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;

public interface UserService {

    String USER_ID_SEQ = "userid";

    User createUser(UserDto userDto);

    User findByUserIdOrEmailOrMobile(Long userId, String email, Long mobile);

    User findOrCreateGoogleUser(GoogleUser gUser);
}
