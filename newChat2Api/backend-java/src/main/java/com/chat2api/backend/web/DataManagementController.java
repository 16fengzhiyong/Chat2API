package com.chat2api.backend.web;

import com.chat2api.backend.repository.AccountRepository;
import com.chat2api.backend.repository.ApiKeyRepository;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.ModelMappingRepository;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.repository.RequestLogRepository;
import com.chat2api.backend.repository.SessionRepository;
import com.chat2api.backend.repository.SystemPromptRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
public class DataManagementController {
    private final ProviderRepository providerRepository;
    private final AccountRepository accountRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ModelMappingRepository modelMappingRepository;
    private final AppConfigRepository appConfigRepository;
    private final RequestLogRepository requestLogRepository;
    private final SessionRepository sessionRepository;
    private final SystemPromptRepository systemPromptRepository;

    public DataManagementController(ProviderRepository providerRepository, AccountRepository accountRepository, ApiKeyRepository apiKeyRepository, ModelMappingRepository modelMappingRepository, AppConfigRepository appConfigRepository, RequestLogRepository requestLogRepository, SessionRepository sessionRepository, SystemPromptRepository systemPromptRepository) {
        this.providerRepository = providerRepository;
        this.accountRepository = accountRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.modelMappingRepository = modelMappingRepository;
        this.appConfigRepository = appConfigRepository;
        this.requestLogRepository = requestLogRepository;
        this.sessionRepository = sessionRepository;
        this.systemPromptRepository = systemPromptRepository;
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", Instant.now().toString());
        data.put("providers", providerRepository.findAll());
        data.put("accounts", accountRepository.findAll());
        data.put("apiKeys", apiKeyRepository.findAll());
        data.put("modelMappings", modelMappingRepository.findAll());
        data.put("config", appConfigRepository.findAll());
        data.put("sessions", sessionRepository.findAll());
        data.put("systemPrompts", systemPromptRepository.findAll());
        data.put("requestLogCount", requestLogRepository.count());
        data.put("note", "Sensitive account credentials are intentionally excluded from export");
        return ApiResponse.ok(data);
    }
}
