package com.app.shahbaztrades.service;

import java.util.Map;

public interface FcmService {
    void sendNotification(String token, String title, String body, Map<String, String> data);
}
