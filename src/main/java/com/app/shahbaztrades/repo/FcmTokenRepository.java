package com.app.shahbaztrades.repo;

import com.app.shahbaztrades.model.entity.FcmToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends MongoRepository<FcmToken, String> {
    List<FcmToken> findByUserId(long userId);

    Optional<FcmToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByTokenAndUserIdNot(String token, long userId);
}