package com.chat2api.backend.web;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/tool-calling")
public class ToolCallingController {
    private static final String CONFIG_KEY = "toolCalling";
    private final AppConfigRepository appConfigRepository;

    public ToolCallingController(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @GetMapping
    public ApiResponse<String> get() {
        return ApiResponse.ok(appConfigRepository.findById(CONFIG_KEY).map(AppConfigEntity::getConfigValue).orElse(defaultConfig()));
    }

    @PostMapping
    public ApiResponse<AppConfigEntity> save(@RequestBody Map<String, Object> request) {
        AppConfigEntity entity = new AppConfigEntity();
        entity.setConfigKey(CONFIG_KEY);
        entity.setConfigValue(String.valueOf(request.getOrDefault("value", defaultConfig())));
        entity.setUpdatedAt(Instant.now());
        return ApiResponse.ok(appConfigRepository.save(entity));
    }

    private String defaultConfig() {
        return "{\"enabled\":true,\"mode\":\"prompt-injection\",\"clientAdapter\":\"openai\",\"diagnostics\":false}";
    }
}
