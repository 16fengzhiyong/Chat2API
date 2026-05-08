package com.chat2api.backend.service;

import com.chat2api.backend.domain.ApiKeyEntity;
import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.domain.ModelMappingEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.chat2api.backend.domain.SystemPromptEntity;
import com.chat2api.backend.repository.AccountRepository;
import com.chat2api.backend.repository.ApiKeyRepository;
import com.chat2api.backend.repository.AppConfigRepository;
import com.chat2api.backend.repository.ModelMappingRepository;
import com.chat2api.backend.repository.ProviderRepository;
import com.chat2api.backend.repository.RequestLogRepository;
import com.chat2api.backend.repository.SessionRepository;
import com.chat2api.backend.repository.SystemPromptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataManagementService {
    private final ProviderRepository providerRepository;
    private final AccountRepository accountRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ModelMappingRepository modelMappingRepository;
    private final AppConfigRepository appConfigRepository;
    private final RequestLogRepository requestLogRepository;
    private final SessionRepository sessionRepository;
    private final SystemPromptRepository systemPromptRepository;
    private final ObjectMapper objectMapper;

    public DataManagementService(ProviderRepository providerRepository, AccountRepository accountRepository, ApiKeyRepository apiKeyRepository, ModelMappingRepository modelMappingRepository, AppConfigRepository appConfigRepository, RequestLogRepository requestLogRepository, SessionRepository sessionRepository, SystemPromptRepository systemPromptRepository, ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.accountRepository = accountRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.modelMappingRepository = modelMappingRepository;
        this.appConfigRepository = appConfigRepository;
        this.requestLogRepository = requestLogRepository;
        this.sessionRepository = sessionRepository;
        this.systemPromptRepository = systemPromptRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> exportData() {
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
        return data;
    }

    @Transactional
    public Map<String, Object> importData(Map<String, Object> payload) {
        ImportCounter counter = new ImportCounter();
        saveList(payload.get("providers"), ProviderEntity.class, item -> {
            providerRepository.save(item);
            counter.providers++;
        });
        saveList(payload.get("apiKeys"), ApiKeyEntity.class, item -> {
            apiKeyRepository.save(item);
            counter.apiKeys++;
        });
        saveList(payload.get("modelMappings"), ModelMappingEntity.class, item -> {
            modelMappingRepository.save(item);
            counter.modelMappings++;
        });
        saveList(payload.get("config"), AppConfigEntity.class, item -> {
            appConfigRepository.save(item);
            counter.config++;
        });
        saveList(payload.get("systemPrompts"), SystemPromptEntity.class, item -> {
            systemPromptRepository.save(item);
            counter.systemPrompts++;
        });
        return Map.of(
                "imported", true,
                "providers", counter.providers,
                "apiKeys", counter.apiKeys,
                "modelMappings", counter.modelMappings,
                "config", counter.config,
                "systemPrompts", counter.systemPrompts,
                "skipped", List.of("accounts", "sessions", "requestLogs")
        );
    }

    private <T> void saveList(Object value, Class<T> type, ImportSink<T> sink) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            T converted = objectMapper.convertValue(item, type);
            sink.save(converted);
        }
    }

    private interface ImportSink<T> {
        void save(T item);
    }

    private static class ImportCounter {
        private int providers;
        private int apiKeys;
        private int modelMappings;
        private int config;
        private int systemPrompts;
    }
}
