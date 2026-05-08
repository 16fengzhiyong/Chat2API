package com.chat2api.backend.repository;

import com.chat2api.backend.domain.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderRepository extends JpaRepository<ProviderEntity, String> {
    List<ProviderEntity> findByEnabledTrue();
}
