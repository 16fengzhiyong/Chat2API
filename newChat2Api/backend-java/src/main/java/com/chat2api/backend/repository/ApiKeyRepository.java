package com.chat2api.backend.repository;

import com.chat2api.backend.domain.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {
    Optional<ApiKeyEntity> findByKeyValueAndEnabledTrue(String keyValue);
}
