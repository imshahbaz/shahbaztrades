package com.app.shahbaztrades.service;

import java.util.Map;

public interface FcmService {
    void saveToken(long userId, String token);

    void removeToken(String token);

    void sendNotification(long userId, String title, String body, Map<String, String> data);
}
