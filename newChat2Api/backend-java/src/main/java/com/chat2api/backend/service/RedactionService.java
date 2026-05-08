package com.chat2api.backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RedactionService {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token", "accessToken", "access_token", "refreshToken", "refresh_token", "cookie", "cookies",
            "sessionToken", "service_token", "ph_token", "authorization", "apiKey", "api_key", "ticket"
    );

    public Map<String, String> maskCredentials(Map<String, String> credentials) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (credentials == null) {
            return masked;
        }
        credentials.forEach((key, value) -> masked.put(key, value == null || value.isBlank() ? value : "***"));
        return masked;
    }

    public String redactText(String input) {
        if (input == null) {
            return null;
        }
        String output = input;
        for (String key : SENSITIVE_KEYS) {
            output = output.replaceAll("(?i)(\\\"?" + key + "\\\"?\\s*[:=]\\s*\\\"?)[^\\\",;\\s}]+", "$1***");
        }
        return output;
    }
}
