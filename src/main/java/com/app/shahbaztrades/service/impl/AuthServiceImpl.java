package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.components.auth.GoogleAuthUtils;
import com.app.shahbaztrades.components.otp.OtpProviderFactory;
import com.app.shahbaztrades.config.security.JwtService;
import com.app.shahbaztrades.exceptions.BadRequestException;
import com.app.shahbaztrades.exceptions.NotFoundException;
import com.app.shahbaztrades.exceptions.UnauthorizedException;
import com.app.shahbaztrades.model.dto.ApiResponse;
import com.app.shahbaztrades.model.dto.UserDto;
import com.app.shahbaztrades.model.dto.auth.AuthRequest;
import com.app.shahbaztrades.model.dto.auth.SignUpResponse;
import com.app.shahbaztrades.model.enums.CacheType;
import com.app.shahbaztrades.model.enums.OtpFor;
import com.app.shahbaztrades.model.enums.OtpType;
import com.app.shahbaztrades.service.AuthService;
import com.app.shahbaztrades.service.MongoConfigService;
import com.app.shahbaztrades.service.UserService;
import com.app.shahbaztrades.util.CacheUtils;
import com.app.shahbaztrades.util.HelperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String AUTH_KEY = "auth_";
    private final StringRedisTemplate stringRedisTemplate;
    private final OtpProviderFactory otpProviderFactory;
    private final Environment environment;
    private final UserService userService;
    private final MongoConfigService mongoConfigService;
    private final GoogleAuthUtils googleAuthUtils;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(AuthRequest request) {
        //TODO validation

        var dto = request.toUserDto();
        var cacheConfig = CacheUtils.getKeyAndExpiry(dto.getEmail(), CacheType.SIGNUP);
        stringRedisTemplate.opsForValue().set(cacheConfig.key(), HelperUtil.GSON.toJson(dto), cacheConfig.expiry());
        var otp = HelperUtil.generateOtp();
        otpProviderFactory.sendOtp(OtpType.EMAIL, dto.getEmail(), otp, OtpFor.REGISTER);
        return ResponseEntity.ok(ApiResponse.ok(SignUpResponse.builder()
                .otpSent(Boolean.TRUE)
                .message("OTP sent to " + dto.getEmail())
                .build(), "OTP sent to " + dto.getEmail()));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> Logout() {
        var cookie = HelperUtil.createAuthCookie("", -1, Objects.equals(environment.getProperty("ENV"), "production"));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    @Override
    public ResponseEntity<ApiResponse<UserDto>> getMe(UserDto dto) {
        var key = AUTH_KEY + dto.getUserId();
        var redisUser = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.isEmpty(redisUser)) {
            var newDto = HelperUtil.GSON.fromJson(redisUser, UserDto.class);
            return ResponseEntity.ok(ApiResponse.ok(newDto, "User details fetched"));
        }

        var user = userService.findByUserIdOrEmailOrMobile(dto.getUserId(), dto.getEmail(), dto.getMobile());
        if (Objects.isNull(user)) {
            throw new UnauthorizedException("User not found");
        }

        var newDto = user.toDto();
        stringRedisTemplate.opsForValue().set(key, HelperUtil.GSON.toJson(newDto), Duration.ofHours(1));
        return ResponseEntity.ok(ApiResponse.ok(newDto, "User details fetched"));
    }

    @Override
    public ResponseEntity<ApiResponse<String>> validateGoogleToken(String code) {
        String id = UUID.randomUUID().toString();
        String signedUuid = HelperUtil.signState(id, mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());

        HelperUtil.EXECUTOR.execute(() -> {
            try {
                var gUser = googleAuthUtils.validateIdToken(code);
                if (Objects.isNull(gUser)) {
                    log.warn("Invalid Google Token");
                    return;
                }

                var user = userService.findOrCreateGoogleUser(gUser);
                stringRedisTemplate.opsForValue().set(AUTH_KEY + id, HelperUtil.GSON.toJson(user), Duration.ofMinutes(2));
            } catch (Exception e) {
                log.error("Failed to find or create google user {}", e.getMessage());
            }
        });

        return ResponseEntity.ok(ApiResponse.ok(signedUuid, "Processing token"));
    }

    @Override
    public ResponseEntity<?> googleAuthCallback(String code, String state) {
        if (state != null && state.startsWith("redirect|")) {
            String[] parts = state.split("\\|");
            if (parts.length == 2) {
                String potentialTarget = parts[1];
                boolean isAllowed = mongoConfigService.getConfig().getFrontendUrls().stream()
                        .anyMatch(potentialTarget::startsWith);

                if (!isAllowed) {
                    throw new BadRequestException("Unauthorized redirect origin");
                }

                String id = UUID.randomUUID().toString();
                String signedUuid = HelperUtil.signState(id, mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());

                HelperUtil.EXECUTOR.execute(() -> {
                    var gUser = googleAuthUtils.googleCallbackProcessing(code, id);
                    if (Objects.isNull(gUser)) {
                        return;
                    }
                    var user = userService.findOrCreateGoogleUser(gUser);
                    stringRedisTemplate.opsForValue().set(AUTH_KEY + id, HelperUtil.GSON.toJson(user), Duration.ofMinutes(2));
                });

                String targetURL = potentialTarget + "/google/callback?code=" + signedUuid + "&state=standard";
                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .header(HttpHeaders.LOCATION, targetURL)
                        .build();
            }
        }

        if ("standard".equals(state)) {
            String id = HelperUtil.extractAndVerify(code,
                    mongoConfigService.getConfig().getGoogleAuth().getEncryptionKey());

            if (id == null) {
                throw new BadRequestException("Invalid or tampered session state");
            }

            String cacheKey = "auth_" + id;
            var redisUser = stringRedisTemplate.opsForValue().get(cacheKey);

            if (StringUtils.isEmpty(redisUser)) {
                throw new NotFoundException("Request still under process or expired");
            }

            UserDto userDto = HelperUtil.GSON.fromJson(redisUser, UserDto.class);
            String tokenStr = jwtService.generateToken(userDto);
            String cookie = HelperUtil.createAuthCookie(tokenStr, 86400, Objects.equals(environment.getProperty("ENV"), "production"));

            stringRedisTemplate.opsForValue().set("auth_" + userDto.getUserId(), HelperUtil.GSON.toJson(userDto), Duration.ofHours(1));
            stringRedisTemplate.delete(cacheKey);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie)
                    .body(ApiResponse.<UserDto>builder()
                            .success(true)
                            .message("User created")
                            .data(userDto)
                            .build());
        }

        throw new UnauthorizedException("Invalid state");
    }

}
