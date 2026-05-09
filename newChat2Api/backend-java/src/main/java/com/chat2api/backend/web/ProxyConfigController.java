package com.chat2api.backend.web;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/proxy/config")
public class ProxyConfigController {
    private static final String CONFIG_KEY = "proxyConfig";
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    public ProxyConfigController(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(appConfigRepository.findById(CONFIG_KEY)
                .map(e -> parseOrDefault(e.getConfigValue()))
                .orElseGet(this::defaultConfig));
    }

    @PostMapping
    public ApiResponse<AppConfigEntity> save(@RequestBody Map<String, Object> request) {
        String value = String.valueOf(request.getOrDefault("value", toJson(defaultConfig())));
        AppConfigEntity entity = new AppConfigEntity();
        entity.setConfigKey(CONFIG_KEY);
        entity.setConfigValue(value);
        entity.setUpdatedAt(Instant.now());
        return ApiResponse.ok(appConfigRepository.save(entity));
    }

    public Map<String, Object> getConfig() {
        return appConfigRepository.findById(CONFIG_KEY)
                .map(e -> parseOrDefault(e.getConfigValue()))
                .orElseGet(this::defaultConfig);
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("requestTimeoutSeconds", 60);
        config.put("retryCount", 3);
        return config;
    }

    private Map<String, Object> parseOrDefault(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> merged = defaultConfig();
            merged.putAll(parsed);
            return merged;
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
