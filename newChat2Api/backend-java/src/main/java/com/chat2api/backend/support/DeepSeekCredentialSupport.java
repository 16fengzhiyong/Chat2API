package com.chat2api.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeepSeekCredentialSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> TOKEN_KEYS = List.of(
            "token",
            "userToken",
            "accessToken",
            "access_token",
            "apiKey",
            "refreshToken",
            "refresh_token",
            "authorization",
            "Authorization"
    );

    private DeepSeekCredentialSupport() {
    }

    public static Map<String, String> normalize(Map<String, String> credentials) {
        Map<String, String> base = new LinkedHashMap<>();
        if (credentials != null) {
            credentials.forEach((key, value) -> {
                String text = value == null ? "" : value.trim();
                if (!text.isBlank()) {
                    base.put(key, text);
                }
            });
        }
        String authorization = firstNonBlank(base.get("authorization"), base.get("Authorization"));
        String cookie = cookie(base);
        String token = token(base);
        if (!authorization.isBlank()) {
            base.put("authorization", authorization);
            base.put("Authorization", authorization);
        }
        if (!cookie.isBlank()) {
            base.put("cookie", cookie);
            base.put("cookies", cookie);
        }
        if (!token.isBlank()) {
            base.put("token", token);
            base.put("userToken", token);
        }
        return base;
    }

    public static String token(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return "";
        }
        for (String key : TOKEN_KEYS) {
            String token = unwrap(credentials.get(key));
            if (!token.isBlank()) {
                return token;
            }
        }
        return "";
    }

    public static String cookie(Map<String, String> credentials) {
        return firstNonBlank(
                credentials == null ? null : credentials.get("cookie"),
                credentials == null ? null : credentials.get("cookies")
        );
    }

    public static String stripBearerPrefix(String value) {
        return value == null ? "" : value.trim().replaceFirst("(?i)^Bearer\\s+", "");
    }

    private static String unwrap(String value) {
        String text = stripBearerPrefix(value);
        if (text.isBlank()) {
            return "";
        }
        if (!looksLikeJson(text)) {
            return text;
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(text, Object.class);
            return tokenFromObject(parsed, 0);
        } catch (Exception ignored) {
            return text;
        }
    }

    private static String tokenFromObject(Object value, int depth) {
        if (depth > 6 || value == null) {
            return "";
        }
        if (value instanceof String text) {
            String stripped = stripBearerPrefix(text);
            if (looksLikeJson(stripped)) {
                try {
                    Object parsed = OBJECT_MAPPER.readValue(stripped, Object.class);
                    String token = tokenFromObject(parsed, depth + 1);
                    if (!token.isBlank()) {
                        return token;
                    }
                } catch (Exception ignored) {
                }
            }
            return stripped;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String token = tokenFromObject(item, depth + 1);
                if (!token.isBlank()) {
                    return token;
                }
            }
            return "";
        }
        if (!(value instanceof Map<?, ?> map)) {
            return "";
        }
        for (String key : List.of("value", "token", "accessToken", "access_token", "userToken", "refreshToken", "refresh_token")) {
            String token = tokenFromObject(map.get(key), depth + 1);
            if (!token.isBlank()) {
                return token;
            }
        }
        for (String key : List.of("data", "biz_data", "payload", "result")) {
            String token = tokenFromObject(map.get(key), depth + 1);
            if (!token.isBlank()) {
                return token;
            }
        }
        return "";
    }

    private static boolean looksLikeJson(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
