package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.ResourceAlreadyExistsException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.GoogleUser;
import com.app.shahbaztrades.model.entity.User;
import com.app.shahbaztrades.model.enums.UserRole;
import com.app.shahbaztrades.model.enums.UserTheme;
import com.app.shahbaztrades.repo.UserRepo;
import com.app.shahbaztrades.service.AuthService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final MongoTemplate mongoTemplate;
    private final UserRepo userRepo;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public User createUser(UserDto userDto) {
        var user = findByUserIdOrEmailOrMobile(userDto.getUserId(), userDto.getEmail(), userDto.getMobile());
        if (user != null) {
            throw new ResourceAlreadyExistsException("User already exists!");
        }

        if (StringUtils.isEmpty(userDto.getPassword())) {
            userDto.setPassword(HelperUtil.generateRandomString(10));
        }

        user = userDto.toEntity();
        user.setUserId(sequenceGeneratorService.getNextSequence(USER_ID_SEQ));
        return userRepo.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByUserIdOrEmailOrMobile(Long userId, String email, Long mobile) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (userId != null && userId > 0) {
            criteriaList.add(Criteria.where(User.Fields.userId).is(userId));
        }

        if (StringUtils.isNotEmpty(email)) {
            criteriaList.add(Criteria.where(User.Fields.email).is(email));
        }

        if (mobile != null && mobile > 0) {
            criteriaList.add(Criteria.where(User.Fields.mobile).is(mobile));
        }

        if (criteriaList.isEmpty()) return null;

        query.addCriteria(new Criteria().orOperator(criteriaList.toArray(new Criteria[0])));
        return mongoTemplate.findOne(query, User.class);
    }

    @Override
    @Transactional
    public User findOrCreateGoogleUser(GoogleUser gUser) {
        User user = this.findByUserIdOrEmailOrMobile(0L, gUser.getEmail(), 0L);

        if (user == null) {
            UserDto dto = UserDto.builder()
                    .email(gUser.getEmail())
                    .username(gUser.getGivenName() + "_" + gUser.getFamilyName())
                    .role(UserRole.USER)
                    .theme(UserTheme.DARK)
                    .name(gUser.getName())
                    .profile(gUser.getPicture())
                    .build();

            return this.createUser(dto);
        }

        if (!user.getProfile().equals(gUser.getPicture())) {
            Query query = new Query(Criteria.where(User.Fields.userId).is(user.getUserId()));
            user.setProfile(gUser.getPicture());
            Update update = new Update();
            update.set(User.Fields.profile, gUser.getPicture());
            mongoTemplate.updateFirst(query, update, User.class);
        }

        return user;
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse<String>> patchFcmToken(UserDto userDto, Map<String, String> request) {
        if (StringUtils.isEmpty(request.get("token"))) {
            throw new BadRequestException("Token is empty!");
        }
        var token = request.get("token");
        Query query = new Query(Criteria.where(User.Fields.userId).is(userDto.getUserId()));
        Update update = new Update();
        update.set(User.Fields.fcmToken, token);
        mongoTemplate.updateFirst(query, update, User.class);
        return ResponseEntity.ok(ApiResponse.ok(token, "FCM token synchronized"));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> updateUserName(UserDto userDto) {
        if (userDto.getUserId() <= 0 || StringUtils.isEmpty(userDto.getUsername())) {
            throw new BadRequestException("Invalid request!");
        }
        Query query = new Query(Criteria.where(User.Fields.userId).is(userDto.getUserId()));
        Update update = new Update();
        update.set(User.Fields.username, userDto.getUsername());
        var result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() < 1) {
            throw new NotFoundException("User not found!");
        }
        stringRedisTemplate.delete(AuthService.AUTH_KEY + userDto.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Username updated successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<UserTheme>> updateUserTheme(UserDto userDto) {
        if (userDto.getUserId() <= 0 || userDto.getTheme() == null) {
            throw new BadRequestException("Invalid request!");
        }
        Query query = new Query(Criteria.where(User.Fields.userId).is(userDto.getUserId()));
        Update update = new Update();
        update.set(User.Fields.theme, userDto.getTheme());
        var result = mongoTemplate.updateFirst(query, update, User.class);
        if (result.getModifiedCount() < 1) {
            throw new NotFoundException("User not found!");
        }
        stringRedisTemplate.delete(AuthService.AUTH_KEY + userDto.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(userDto.getTheme(), "Theme synchronized"));
    }

}
