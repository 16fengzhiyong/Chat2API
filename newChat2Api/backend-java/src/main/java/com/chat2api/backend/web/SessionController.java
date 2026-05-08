package com.chat2api.backend.web;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.domain.SessionEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.SessionRepository;
import com.chat2api.backend.service.SessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionRepository sessionRepository;
    private final AppConfigRepository appConfigRepository;
    private final SessionService sessionService;

    public SessionController(SessionRepository sessionRepository, AppConfigRepository appConfigRepository, SessionService sessionService) {
        this.sessionRepository = sessionRepository;
        this.appConfigRepository = appConfigRepository;
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<List<SessionEntity>> list() {
        return ApiResponse.ok(sessionRepository.findAll());
    }

    @GetMapping("/active")
    public ApiResponse<List<SessionEntity>> active() {
        return ApiResponse.ok(sessionRepository.findAll().stream().filter(session -> "active".equals(session.getStatus())).toList());
    }

    @GetMapping("/account/{accountId}")
    public ApiResponse<List<SessionEntity>> byAccount(@PathVariable String accountId) {
        return ApiResponse.ok(sessionRepository.findByAccountId(accountId));
    }

    @GetMapping("/provider/{providerId}")
    public ApiResponse<List<SessionEntity>> byProvider(@PathVariable String providerId) {
        return ApiResponse.ok(sessionRepository.findByProviderId(providerId));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.ok(sessionService.config());
    }

    @PostMapping("/config")
    public ApiResponse<AppConfigEntity> saveConfig(@RequestBody Map<String, Object> request) {
        AppConfigEntity entity = new AppConfigEntity();
        entity.setConfigKey("sessionManagement");
        entity.setConfigValue(String.valueOf(request.getOrDefault("value", sessionService.defaultConfigJson())));
        entity.setUpdatedAt(Instant.now());
        return ApiResponse.ok(appConfigRepository.save(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        sessionRepository.deleteById(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> clearAll() {
        sessionRepository.deleteAll();
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
