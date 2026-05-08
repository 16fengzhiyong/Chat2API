package com.chat2api.backend.service;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContextManagementService {
    private static final String CONFIG_KEY = "contextManagement";
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    public ContextManagementService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> apply(Map<String, Object> request) {
        Map<String, Object> config = config();
        if (!Boolean.TRUE.equals(config.get("enabled"))) {
            return request;
        }
        List<Map<String, Object>> messages = messages(request.get("messages"));
        int originalCount = messages.size();
        messages = applySlidingWindow(messages, config);
        messages = applyTokenLimit(messages, config);
        if (messages.size() == originalCount) {
            return request;
        }
        Map<String, Object> updated = new LinkedHashMap<>(request);
        updated.put("messages", messages);
        updated.put("chat2api_context", Map.of("originalCount", originalCount, "finalCount", messages.size()));
        return updated;
    }

    public Map<String, Object> config() {
        return appConfigRepository.findById(CONFIG_KEY)
                .map(AppConfigEntity::getConfigValue)
                .map(this::parseConfig)
                .orElseGet(this::defaultConfig);
    }

    public String defaultConfigJson() {
        try {
            return objectMapper.writeValueAsString(defaultConfig());
        } catch (Exception error) {
            return "{}";
        }
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> merged = defaultConfig();
            merged.putAll(parsed);
            return merged;
        } catch (Exception error) {
            return defaultConfig();
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", false);
        config.put("slidingWindowEnabled", true);
        config.put("maxMessages", 20);
        config.put("tokenLimitEnabled", false);
        config.put("maxTokens", 4000);
        config.put("summaryEnabled", false);
        config.put("keepRecentMessages", 20);
        return config;
    }

    private List<Map<String, Object>> applySlidingWindow(List<Map<String, Object>> messages, Map<String, Object> config) {
        if (!Boolean.TRUE.equals(config.get("slidingWindowEnabled"))) {
            return messages;
        }
        int maxMessages = intValue(config.get("maxMessages"), 20);
        if (messages.size() <= maxMessages) {
            return messages;
        }
        List<Map<String, Object>> systemMessages = messages.stream().filter(message -> "system".equals(message.get("role"))).toList();
        List<Map<String, Object>> nonSystemMessages = messages.stream().filter(message -> !"system".equals(message.get("role"))).toList();
        int keepNonSystem = Math.max(0, maxMessages - systemMessages.size());
        List<Map<String, Object>> result = new ArrayList<>(systemMessages);
        result.addAll(nonSystemMessages.subList(Math.max(0, nonSystemMessages.size() - keepNonSystem), nonSystemMessages.size()));
        return result;
    }

    private List<Map<String, Object>> applyTokenLimit(List<Map<String, Object>> messages, Map<String, Object> config) {
        if (!Boolean.TRUE.equals(config.get("tokenLimitEnabled"))) {
            return messages;
        }
        int maxTokens = intValue(config.get("maxTokens"), 4000);
        List<Map<String, Object>> systemMessages = messages.stream().filter(message -> "system".equals(message.get("role"))).toList();
        List<Map<String, Object>> nonSystemMessages = messages.stream().filter(message -> !"system".equals(message.get("role"))).toList();
        int systemTokens = systemMessages.stream().mapToInt(this::estimateTokens).sum();
        int available = Math.max(0, maxTokens - systemTokens);
        List<Map<String, Object>> kept = new ArrayList<>();
        int used = 0;
        for (int i = nonSystemMessages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = nonSystemMessages.get(i);
            int tokens = estimateTokens(message);
            if (used + tokens > available) {
                break;
            }
            kept.add(0, message);
            used += tokens;
        }
        List<Map<String, Object>> result = new ArrayList<>(systemMessages);
        result.addAll(kept);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> message = new LinkedHashMap<>();
                map.forEach((key, val) -> message.put(String.valueOf(key), val));
                result.add(message);
            }
        }
        return result;
    }

    private int estimateTokens(Map<String, Object> message) {
        Object content = message.get("content");
        if (content == null) {
            return 0;
        }
        return Math.max(1, String.valueOf(content).length() / 3);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception error) {
            return fallback;
        }
    }
}
