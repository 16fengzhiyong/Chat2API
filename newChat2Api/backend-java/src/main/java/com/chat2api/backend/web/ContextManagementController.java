package com.chat2api.backend.web;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.service.ContextManagementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/context-management")
public class ContextManagementController {
    private final AppConfigRepository appConfigRepository;
    private final ContextManagementService contextManagementService;

    public ContextManagementController(AppConfigRepository appConfigRepository, ContextManagementService contextManagementService) {
        this.appConfigRepository = appConfigRepository;
        this.contextManagementService = contextManagementService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(contextManagementService.config());
    }

    @PostMapping
    public ApiResponse<AppConfigEntity> save(@RequestBody Map<String, Object> request) {
        AppConfigEntity entity = new AppConfigEntity();
        entity.setConfigKey("contextManagement");
        entity.setConfigValue(String.valueOf(request.getOrDefault("value", contextManagementService.defaultConfigJson())));
        entity.setUpdatedAt(Instant.now());
        return ApiResponse.ok(appConfigRepository.save(entity));
    }
}
