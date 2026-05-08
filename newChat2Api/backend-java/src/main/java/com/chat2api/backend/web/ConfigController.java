package com.chat2api.backend.web;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final AppConfigRepository appConfigRepository;

    public ConfigController(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @GetMapping
    public ApiResponse<List<AppConfigEntity>> list() {
        return ApiResponse.ok(appConfigRepository.findAll());
    }

    @PostMapping
    public ApiResponse<AppConfigEntity> save(@RequestBody Map<String, Object> request) {
        AppConfigEntity entity = new AppConfigEntity();
        entity.setConfigKey(String.valueOf(request.get("key")));
        entity.setConfigValue(String.valueOf(request.getOrDefault("value", "{}")));
        entity.setUpdatedAt(Instant.now());
        return ApiResponse.ok(appConfigRepository.save(entity));
    }
}
