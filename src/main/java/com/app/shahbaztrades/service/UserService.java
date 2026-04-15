package com.app.shahbaztrades.service;

import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserTheme;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface UserService {

    String USER_ID_SEQ = "userid";

    User createUser(UserDto userDto);

    User findByUserIdOrEmailOrMobile(Long userId, String email, Long mobile);

    User findOrCreateGoogleUser(GoogleUser gUser);

    ResponseEntity<ApiResponse<String>> patchFcmToken(UserDto userDto, Map<String, String> request);

    ResponseEntity<ApiResponse<Void>> updateUserName(UserDto userDto);

    ResponseEntity<ApiResponse<UserTheme>> updateUserTheme(UserDto userDto);
}
