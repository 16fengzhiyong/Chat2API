package com.chat2api.backend.service;

import com.chat2api.backend.domain.ApiKeyEntity;
import com.chat2api.backend.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final IdService idService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, IdService idService) {
        this.apiKeyRepository = apiKeyRepository;
        this.idService = idService;
    }

    public List<ApiKeyEntity> list() {
        return apiKeyRepository.findAll();
    }

    public boolean hasKeys() {
        return apiKeyRepository.count() > 0;
    }

    public ApiKeyEntity create(String name, String description, Object allowedModels) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(idService.id("key"));
        entity.setName(name);
        entity.setDescription(description);
        entity.setKeyValue(idService.secret("c2a"));
        entity.setAllowedModels(normalizeAllowedModels(allowedModels));
        return apiKeyRepository.save(entity);
    }

    @Transactional
    public Optional<ApiKeyEntity> verify(String key) {
        Optional<ApiKeyEntity> apiKey = apiKeyRepository.findByKeyValueAndEnabledTrue(key);
        apiKey.ifPresent(entity -> {
            entity.setUsageCount(entity.getUsageCount() + 1);
            entity.setLastUsedAt(Instant.now());
            apiKeyRepository.save(entity);
        });
        return apiKey;
    }

    public ApiKeyEntity update(String id, Map<String, Object> request) {
        ApiKeyEntity entity = apiKeyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("API key not found: " + id));
        if (request.containsKey("enabled")) {
            entity.setEnabled(Boolean.parseBoolean(String.valueOf(request.get("enabled"))));
        }
        if (request.containsKey("name") && request.get("name") != null) {
            entity.setName(String.valueOf(request.get("name")));
        }
        if (request.containsKey("description")) {
            entity.setDescription(request.get("description") == null ? null : String.valueOf(request.get("description")));
        }
        if (request.containsKey("allowedModels")) {
            entity.setAllowedModels(normalizeAllowedModels(request.get("allowedModels")));
        }
        return apiKeyRepository.save(entity);
    }

    public boolean isModelAllowed(ApiKeyEntity apiKey, String model) {
        if (apiKey == null || apiKey.getAllowedModels() == null || apiKey.getAllowedModels().isEmpty()) {
            return true;
        }
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalizedModel = model.trim().toLowerCase(Locale.ROOT);
        return apiKey.getAllowedModels().stream().anyMatch(allowed -> {
            String normalizedAllowed = allowed == null ? "" : allowed.trim().toLowerCase(Locale.ROOT);
            if (normalizedAllowed.isEmpty() || "all".equals(normalizedAllowed)) {
                return true;
            }
            if (normalizedAllowed.endsWith("*")) {
                return normalizedModel.startsWith(normalizedAllowed.substring(0, normalizedAllowed.length() - 1));
            }
            return normalizedAllowed.equals(normalizedModel);
        });
    }

    private List<String> normalizeAllowedModels(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        Set<String> models = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            String model = String.valueOf(item).trim();
            if (model.isEmpty()) {
                continue;
            }
            if ("all".equalsIgnoreCase(model)) {
                return List.of();
            }
            models.add(model);
        }
        return List.copyOf(models);
    }

    public void delete(String id) {
        apiKeyRepository.deleteById(id);
    }
}
