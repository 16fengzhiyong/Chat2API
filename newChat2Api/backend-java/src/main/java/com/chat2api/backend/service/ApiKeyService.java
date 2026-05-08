package com.chat2api.backend.service;

import com.chat2api.backend.domain.ApiKeyEntity;
import com.chat2api.backend.repository.ApiKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    public ApiKeyEntity create(String name, String description) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(idService.id("key"));
        entity.setName(name);
        entity.setDescription(description);
        entity.setKeyValue(idService.secret("c2a"));
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

    public void delete(String id) {
        apiKeyRepository.deleteById(id);
    }
}
