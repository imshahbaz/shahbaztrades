package com.shahbaz.trades.service.impl;

import com.shahbaz.trades.model.dto.UserDto;
import com.shahbaz.trades.model.entity.User;
import com.shahbaz.trades.repository.UserRepository;
import com.shahbaz.trades.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto createUser(UserDto request) {
        Optional<User> optionalUser = userRepository.findById(request.getEmail());
        if (optionalUser.isPresent()) {
            throw new RuntimeException("User already exists");
        }
        User user = request.toEntity();
        userRepository.save(user);
        return user.toDto();
    }

    @Override
    public UserDto updateUser(UserDto request) {
        User user = userRepository.findById(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        userRepository.save(user);
        return user.toDto();
    }

    @Override
    public UserDto getUser(String email) {
        User user = userRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.toDto();
    }

    @Override
    @Transactional
    public UserDto updateUserTheme(String email, User.Theme theme) {
        User user = userRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setTheme(theme);
        userRepository.save(user);
        return user.toDto();
    }

    @Override
    @Transactional
    public UserDto updateUsername(String email, String username) {
        User user = userRepository.findById(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setUsername(username);
        userRepository.save(user);
        return user.toDto();
    }

    @Override
    public void deleteUser(String email) {
        userRepository.deleteById(email);
    }

}
